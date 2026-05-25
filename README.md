# MeetLoggerV2

MeetLoggerV2 is an Android application designed to help users capture, manage, and interact with meeting artifacts such as audio recordings and processed documents. The project integrates cloud-backed storage and collaboration features while providing a polished mobile experience.

## Project Overview

The app is built with Kotlin and Android Jetpack components, with a focus on clean separation between UI, data handling, and backend integration.

Key capabilities:

- Google-based user authentication
- Cloud persistence for user files and audio assets
- Audio upload, download, playback, and remote processing support
- Document export for meeting notes and processed transcripts
- Notifications for processed files
- Firebase analytics and crash reporting

## Architecture

This repository is structured as a standard Android application with one primary module:

- `app/`
    - `src/main/java/com/example/meetloggerv2/`
        - `core/` &mdash; shared utilities, session management, media helpers, network helpers
        - `data/` &mdash; repositories, remote API integration, data models
        - `ui/` &mdash; activity and fragment hierarchy for login, home, and audio flows
        - `MeetLoggerApp.kt` &mdash; application-level initialization and notification orchestration

The app follows an MVVM-style organization, with repositories handling backend and local data operations, view models exposing UI state, and activities/fragments driving presentation.

## Technology Stack

- Kotlin
- AndroidX / Jetpack libraries
- Firebase Authentication, Firestore, Storage, Crashlytics
- Google Sign-In
- Retrofit + OkHttp
- Glide and Picasso for image handling
- ML Kit Translate
- iText and Apache POI for document export

## Setup

### Requirements

- Android Studio (latest stable release recommended)
- Java 11 / JDK 11
- Android SDK 37
- Gradle wrapper provided in the repository

### Getting Started

1. Clone the repository:
    ```bash
    git clone https://github.com/Mutharasan-PSG/MeetLoggerv2.git
    cd MeetLoggerv2
    ```
2. Open the project in Android Studio.
3. Allow Android Studio to sync Gradle and resolve dependencies.
4. Confirm the `google-services.json` file is present under `app/` and configured for the Firebase project.
5. Build and run the app on an emulator or device.

## Configuration

The app uses a backend endpoint configured via Gradle build constants. This endpoint is defined in `app/build.gradle.kts` as `BuildConfig.BASE_URL`.

Firebase initialization occurs in `MeetLoggerApp.kt`, and several features depend on Firebase services being available.

## How to Use

- Launch the app and sign in with Google.
- Create or upload meeting audio files.
- Track file processing status in the home view.
- Export processed documents into shareable formats.
- Receive notifications when files are ready.

## Notes

- The app requests runtime permissions for audio recording, file access, and notifications.
- Firestore is used for user file metadata and real-time updates.
- Cloud Storage is used for audio file storage.
- Crashlytics is enabled for diagnostics and crash tracking.

## Development Notes

- The app is configured for `compileSdk = 37`, `minSdk = 26`, and `targetSdk = 37`.
- ViewBinding and DataBinding are enabled for UI binding in layouts.
- The project uses the Gradle Version Catalog for dependency management.

## Contribution

If you are extending this project, focus on keeping feature logic inside repositories and avoid placing backend-specific workflows directly in UI classes. Use the existing package structure to maintain separation between UI, data, and core utilities.

---

## License

This repository owned by Mutharasan [Github - Mutharasan-PSG]
