//
//  ContentView.swift
//  HabitBellTV
//
//  Architectural Role:
//  Declarative SwiftUI user interface engineered for the 10-foot Apple TV living room experience.
//  Displays high-contrast circular countdown animation, next interval bell indicators,
//  and provides Siri Remote D-Pad / Clickpad focus interactions.
//

import SwiftUI

struct ContentView: View {
    @EnvironmentObject var stateListener: TVStateListener

    var body: some View {
        ZStack {
            Color(red: 0.02, green: 0.03, blue: 0.04)
                .ignoresSafeArea()

            VStack(spacing: 40) {
                // Header Bar
                HStack {
                    Text("HABIT BELL • TV")
                        .font(.caption)
                        .fontWeight(.bold)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color(red: 0.90, green: 0.66, blue: 0.24).opacity(0.15))
                        .foregroundColor(Color(red: 0.90, green: 0.66, blue: 0.24))
                        .cornerRadius(20)

                    Spacer()

                    Text(stateListener.profileName)
                        .font(.title2)
                        .foregroundColor(.white.opacity(0.9))

                    Spacer()

                    HStack(spacing: 8) {
                        Circle()
                            .fill(stateListener.isConnected ? Color.green : Color.orange)
                            .frame(width: 12, height: 12)
                        Text(stateListener.isConnected ? "Synced via Wi-Fi" : "Connecting...")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }
                .padding(.horizontal, 60)
                .padding(.top, 40)

                Spacer()

                // Center Stage: Circular Countdown Ring
                ZStack {
                    Circle()
                        .stroke(Color.white.opacity(0.08), lineWidth: 16)
                        .frame(width: 480, height: 480)

                    Circle()
                        .trim(from: 0.0, to: CGFloat(stateListener.progressFraction))
                        .stroke(
                            Color(red: 0.90, green: 0.66, blue: 0.24),
                            style: StrokeStyle(lineWidth: 16, lineCap: .round)
                        )
                        .rotationEffect(.degrees(-90))
                        .frame(width: 480, height: 480)
                        .animation(.linear(duration: 1.0), value: stateListener.progressFraction)

                    VStack(spacing: 12) {
                        Text(stateListener.formattedTime)
                            .font(.system(size: 96, weight: .thin, design: .rounded))
                            .foregroundColor(.white)

                        if !stateListener.formattedNextBell.isEmpty {
                            Text(stateListener.formattedNextBell)
                                .font(.title3)
                                .fontWeight(.medium)
                                .foregroundColor(Color(red: 0.90, green: 0.66, blue: 0.24))
                        }
                    }
                }

                Spacer()

                // Controls: Siri Remote Compatible
                HStack(spacing: 30) {
                    Button(action: {
                        stateListener.reset()
                    }) {
                        Text("Reset")
                            .font(.headline)
                            .frame(width: 180, height: 60)
                    }

                    Button(action: {
                        stateListener.togglePlayPause()
                    }) {
                        Text(stateListener.status == "RUNNING" ? "Pause" : "Start")
                            .font(.headline)
                            .fontWeight(.bold)
                            .frame(width: 220, height: 60)
                    }
                }
                .padding(.bottom, 60)
            }
        }
    }
}
