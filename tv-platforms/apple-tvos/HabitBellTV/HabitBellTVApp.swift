//
//  HabitBellTVApp.swift
//  HabitBellTV
//
//  Architectural Role:
//  Main entrypoint for the native Apple TV (tvOS 17+) application.
//  Initializes the ambient tvOS environment, manages application lifecycle,
//  and orchestrates the network synchronization listener bound to the living room display.
//

import SwiftUI

@main
struct HabitBellTVApp: App {
    @StateObject private var stateListener = TVStateListener()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(stateListener)
                .preferredColorScheme(.dark)
                .onAppear {
                    stateListener.startDiscovery()
                }
        }
    }
}
