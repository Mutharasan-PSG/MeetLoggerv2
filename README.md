# MeetLogger V2 — Android Client

MeetLogger V2 is a secure, enterprise-grade Android application designed for the capture, management, and processing of meeting sessions. It enables users to record or import meeting audio, interface with cloud services for automatic minutes extraction, perform on-device offline translations, and export polished meeting minutes.

## Key Capabilities

- **Intelligent Audio Capture**: Record high-fidelity meeting audio locally on the device or upload pre-recorded meeting files.
- **Enterprise-Grade Meeting Summaries**: Generate comprehensive summaries, actionable follow-ups, decisions, and speaker-attributed transcripts.
- **Secure Cloud Synchronization**: Real-time cloud sync of documents and resources backed by robust authentication.
- **On-Device Offline Translation**: Powerful offline translation engines supporting multiple languages for seamless collaboration.
- **Polished Document Exporting & Sharing**: Export meeting summaries into professional presentation-ready PDF, DOCX, or text files, and share them directly from the app.
- **Smart Notification Workflows**: Real-time delivery status updates for meeting minutes processing via push notifications.
- **Usage & Plan Controls**: Automated features for free tier limits and pro subscription levels.

## Setup & Deployment

### Prerequisites
- **IDE**: Android Studio (Koala / Ladybug or later)
- **JDK**: Java Development Kit (JDK) 11 or later
- **Android SDK**: Target SDK 37 (Android 15), Min SDK 26 (Android 8.0)

### Getting Started
1. Open the project in Android Studio.
2. Add your Firebase configuration file (`google-services.json`) to the app module.
3. Synchronize Gradle dependencies.
4. Build and run the app on a connected mobile device or emulator.

## Testing

The project has a comprehensive unit testing suite coverage (>210 tests) running on the JVM. For full details on tests, mock configurations, and target test cases, check out the [Unit Testing Documentation](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/TESTING.md).

To run all unit tests in the client app:
```bash
./gradlew testDebugUnitTest
```

## License & Ownership

This project is owned and maintained by **Mutharasan** ([GitHub Profile](https://github.com/Mutharasan-PSG)).

### Copyright & Terms
Copyright © 2026 Mutharasan. All rights reserved.
All materials and source codes are protected under international intellectual property and copyright laws. Unauthorized duplication, redistribution, or modification of this project, in whole or in part, without the explicit prior written consent of the owner is strictly prohibited.
