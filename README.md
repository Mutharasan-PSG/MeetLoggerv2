# MeetLoggerV2

MeetLoggerV2 is a robust Android application designed to help users capture, manage, and interact with meeting artifacts such as audio recordings and processed documents. It integrates cloud-backed storage, remote AI document processing pipelines, and collaboration features, all delivered through a modern, responsive mobile interface.

---

## Key Features

- **Audio Capture & Management**: Record meeting conversations directly on-device or upload existing audio files for processing.
- **AI-Powered Document Processing**: Seamlessly interface with remote services to generate structured meeting summaries, detailed action items, and complete transcripts.
- **Cloud Synchronization**: Secure authentication and automatic real-time syncing of user files, metadata, and audio resources.
- **Multilingual Support**: On-device offline translation powered by machine learning models to facilitate cross-lingual collaboration.
- **Flexible Document Export & Sharing**: Export meeting reports into industry-standard formats (PDF and DOCX) and share them instantly using system sharing channels.

---

## Architectural Principles

The application is engineered following clean software architecture guidelines and modern Android development best practices.

### 1. Model-View-ViewModel (MVVM)
The project enforces a strict unidirectional data flow. The user interface remains passive and responsive to state observers, while the business logic, state lifecycle management, and background task orchestrations are isolated inside the presentation models.

### 2. Dependency Inversion
UI and business rules are decoupled from concrete database, authentication, and network mechanisms. High-level modules depend on abstractions (interfaces) rather than concrete details, making the codebase highly testable and maintainable.

### 3. Open/Closed & Strategy Patterns
Extensible systems like document exporting are designed to be open for extension but closed for modification. New document formats and export rules can be introduced independently by adding strategy implementations without altering core orchestration logic.

---

## Directory Structure

The project follows a standard module separation structure:

```
app/
 └─ src/main/java/com/example/meetloggerv2/
     ├─ core/             # Shared utilities, session management, and hardware wrappers
     │   ├─ export/       # Document export engines
     │   ├─ media/        # Device media and recording interfaces
     │   ├─ network/      # API communication clients and connectivity monitors
     │   └─ util/         # General helper classes and UI extensions
     ├─ data/             # Data access layer
     │   ├─ local/        # Local preferences and configuration datastores
     │   ├─ model/        # Domain data representations
     │   ├─ remote/       # API contract definitions
     │   └─ repository/   # Repository boundaries (abstractions and concrete data sources)
     └─ ui/               # Presentation layer (Activities, Fragments, and view adapters)
```

---

## Technology Stack

- **Platform & Core**: Kotlin, AndroidX, Jetpack Lifecycle Components
- **Local Storage**: Jetpack Preferences DataStore
- **Cloud Infrastructure**: Firebase (Authentication, Firestore, Cloud Storage, Cloud Messaging)
- **Networking**: Retrofit, OkHttp, GSON
- **ML & Parsing**: Google ML Kit Translation, iText 7, Apache POI
- **Diagnostics**: Firebase Crashlytics & Analytics

---

## Setup & Run Instructions

### Prerequisites
- **IDE**: Android Studio (Koala / Ladybug or later)
- **JDK**: Java Development Kit (JDK) 11
- **Android SDK**: Compile & Target SDK 37 (Android 15), Minimum SDK 26 (Android 8.0)

### Getting Started
1. Clone the repository:
   ```bash
   git clone https://github.com/Mutharasan-PSG/MeetLoggerv2.git
   cd MeetLoggerv2
   ```
2. Open the project directory in Android Studio.
3. Verify that your `google-services.json` file is placed in the `app/` module directory.
4. Synchronize the Gradle files and build the project:
   ```bash
   ./gradlew compileDebugSources
   ```
5. Run the application on an emulator or a connected development device.

---

## Contribution & Code Quality Guidelines

When extending the codebase, please adhere to the following principles:
- **Keep Views Dumb**: Views (Fragments and Activities) should only bind data and handle layout updates.
- **Implement via Abstractions**: Define interface abstractions for data sources and business tasks before wiring them to presentation models.
- **Extract Common Routines**: Avoid repeating visual layouts, dialog logic, or formatting utilities; leverage the common utilities package.

---

## Ownership & License

This project is owned and maintained by **Mutharasan** ([GitHub Profile](https://github.com/Mutharasan-PSG)).

### Copyright & Intellectual Property
Copyright © 2026 Mutharasan. All rights reserved.

All materials and code in this repository are protected under international copyright laws. 
- **Unauthorized Duplication**: Copying, cloning, distributing, or modifying this project (or parts of it) for commercial publication, redistribution, or any public deployment without the explicit prior written consent of the owner is strictly prohibited.
- **Academic & Reference Use**: If referencing this repository for educational purposes, proper attribution to the original author and the GitHub repository link must be provided.

### Terms & Anti-Misuse Policy
This software is provided "as is" without warranty of any kind, express or implied.
- **Responsible Use**: This application handles audio recording, document processing, and cloud storage. Users are solely responsible for ensuring they have legal consent from all meeting participants before capturing or storing audio data.
- **Liability**: Under no circumstances shall the author or copyright holder be held liable for any claims, damages, privacy violations, or other liabilities arising from the misuse or unauthorized distribution of this application.
