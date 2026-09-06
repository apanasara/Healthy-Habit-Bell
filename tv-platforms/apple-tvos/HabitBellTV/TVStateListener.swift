//
//  TVStateListener.swift
//  HabitBellTV
//
//  Architectural Role:
//  Network state synchronization coordinator for Apple TV (tvOS).
//  Discovers the Habit Bell Android handset over the local Wi-Fi via Bonjour
//  mDNS (_http._tcp / _habitbell._tcp) and continuously synchronizes countdown state,
//  interval bells, and transport control events with zero cloud latency.
//

import Foundation
import Combine

public class TVStateListener: NSObject, ObservableObject, NetServiceBrowserDelegate, NetServiceDelegate {
    @Published public var status: String = "IDLE"
    @Published public var profileName: String = "Mindful Eating"
    @Published public var remainingSeconds: Int = 1200
    @Published public var totalSeconds: Int = 1200
    @Published public var formattedTime: String = "20:00"
    @Published public var formattedNextBell: String = "Next Bell in 00:30"
    @Published public var progressFraction: Double = 0.0
    @Published public var isConnected: BooleanLiteralType = false

    private var hostAddress: String = "192.168.1.2"
    private let port: Int = 8888
    private var timer: AnyCancellable?
    private var serviceBrowser: NetServiceBrowser?
    private var discoveredService: NetService?

    public override init() {
        super.init()
    }

    /// Initiates Bonjour discovery to auto-detect the Android handset broadcasting on the LAN.
    public func startDiscovery() {
        serviceBrowser = NetServiceBrowser()
        serviceBrowser?.delegate = self
        serviceBrowser?.searchForServices(ofType: "_http._tcp.", inDomain: "local.")
        
        // Begin immediate polling loop against current host
        startPolling()
    }

    public func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        if service.name.contains("HabitBell") {
            discoveredService = service
            service.delegate = self
            service.resolve(withTimeout: 5.0)
        }
    }

    public func netServiceDidResolveAddress(_ sender: NetService) {
        if let hostName = sender.hostName {
            self.hostAddress = hostName
            self.isConnected = true
        }
    }

    private func startPolling() {
        timer = Timer.publish(every: 1.0, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] _ in
                self?.fetchState()
            }
    }

    public func fetchState() {
        guard let url = URL(string: "http://\(hostAddress):\(port)/api/state") else { return }
        URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
            guard let data = data, error == nil else { return }
            do {
                if let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    DispatchQueue.main.async {
                        self?.status = json["status"] as? String ?? "RUNNING"
                        self?.profileName = json["profileName"] as? String ?? "Session"
                        self?.remainingSeconds = json["remainingSeconds"] as? Int ?? 0
                        self?.totalSeconds = json["totalSeconds"] as? Int ?? 1200
                        self?.formattedTime = json["formattedTime"] as? String ?? "00:00"
                        self?.formattedNextBell = json["formattedNextBell"] as? String ?? ""
                        self?.progressFraction = json["progressFraction"] as? Double ?? 0.0
                        self?.isConnected = true
                    }
                }
            } catch {
                // Ignore parse errors on transient drops
            }
        }.resume()
    }

    public func togglePlayPause() {
        guard let url = URL(string: "http://\(hostAddress):\(port)/api/action/toggle") else { return }
        URLSession.shared.dataTask(with: url).resume()
    }

    public func reset() {
        guard let url = URL(string: "http://\(hostAddress):\(port)/api/action/stop") else { return }
        URLSession.shared.dataTask(with: url).resume()
    }
}
