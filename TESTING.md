# MeetLogger V2 — Unit Testing Documentation

This document provides a comprehensive guide to the unit testing architecture, coverage, platform class mocking strategies, and execution guidelines for the **MeetLogger V2** Android client application.

---

## 1. Test Architecture Overview

All unit tests are written to run on the JVM without requiring a physical Android device or emulator. The testing framework is designed around:
- **JUnit 4**: The test runner.
- **MockK**: The mocking library for Kotlin, used to stub repository APIs, Android framework classes, and static utility methods.
- **Kotlin Coroutines Test**: Standard library components (`runTest`, `StandardTestDispatcher`) for managing coroutines in tests.
- **Turbine**: A small, library-focused testing utility for Kotlin Flow verification, enabling easy assertion on StateFlow and SharedFlow emissions.

### Test Runner Configuration
Tests are executed using the local JVM target. Since many classes reference Android platform primitives, custom mock initializers are defined in the test configuration blocks to avoid runtime method stub exceptions.

---

## 2. Running Unit Tests

### Command Line Execution
To run all unit tests across all project modules, execute the following command in the root of the project:
```bash
./gradlew testDebugUnitTest
```

To run tests for a specific module:
- **App Module**: `./gradlew :app:testDebugUnitTest`
- **Data Module**: `./gradlew :data:testDebugUnitTest`
- **Dashboard Feature**: `./gradlew :feature:dashboard:testDebugUnitTest`
- **Details Feature**: `./gradlew :feature:details:testDebugUnitTest`
- **Report Feature**: `./gradlew :feature:report:testDebugUnitTest`
- **Auth Feature**: `./gradlew :feature:auth:testDebugUnitTest`

### HTML Test Reports
Gradle automatically generates HTML test execution reports after running the tests. You can review the details by opening:
```
<module_name>/build/reports/tests/testDebugUnitTest/index.html
```

---

## 3. Platform Class Mocking (JVM Workarounds)

Standard JVM tests do not include Android platform class implementations by default, which throws `RuntimeException` for framework dependencies. We successfully bypass these limitations using static MockK mocks inside `@Before` setup methods:

### A. Android Logs Mocking (`Log.d`, `Log.e`)
```kotlin
mockkStatic(android.util.Log::class)
every { android.util.Log.d(any(), any()) } returns 0
every { android.util.Log.e(any(), any()) } returns 0
every { android.util.Log.e(any(), any(), any()) } returns 0
```
*Why*: Prevents JVM crashes when ViewModels log execution trace states.

### B. String Utility Mocking (`TextUtils.isEmpty`)
```kotlin
mockkStatic(android.text.TextUtils::class)
every { android.text.TextUtils.isEmpty(any()) } answers {
    val s = firstArg<CharSequence?>()
    s == null || s.length == 0
}
```
*Why*: Essential when checking validity constraints during `DataStore` initialization inside tests.

### C. Looper Mocking (`Looper.getMainLooper`)
```kotlin
mockkStatic(android.os.Looper::class)
every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)
```
*Why*: Allows the GMS (Google Play Services) API objects like `GoogleSignInClient` to initialize without crash.

### D. WorkManager Progress Future Mocking
```kotlin
val progressUpdater = mockk<androidx.work.ProgressUpdater>()
val mockFuture = mockk<com.google.common.util.concurrent.ListenableFuture<Void>>()
every { mockFuture.isDone } returns true
every { mockFuture.get() } returns null
every { workerParams.progressUpdater } returns progressUpdater
every { progressUpdater.updateProgress(any(), any(), any()) } returns mockFuture
```
*Why*: Forces coroutine-based worker progress updates (`setProgress()`) to resolve immediately instead of suspending indefinitely.

---

## 4. Coverage Breakdown by Module & Scenarios

Every critical logical branch, network fallback, cache check, and state change has been fully tested:

### 1. `feature/auth` Module

- **[LoginViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/auth/src/test/java/com/meetloggerv2/ui/login/viewmodel/LoginViewModelTest.kt)**:
  - **Social Credentials Login**: Verifies credentials checks, user profile retrieval, and transition to success state.
  - **Account Verification**: Ensures that logging in with an email checks the backend registration records first.
  - **Unregistered Users**: Verifies redirection to signup state when checking emails not in the database.
  - **Email Signup Flow**: Stubbed registration callbacks, verifies sending authentication verification links, and updates local profile records.
  - **Password Reset**: Tests password reset dispatch states (Google account restriction alerts vs email reset sending).

---

### 2. `feature/dashboard` Module

- **[AudioListViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/dashboard/src/test/java/com/meetloggerv2/ui/audio/viewmodel/AudioListViewModelTest.kt)**:
  - **File List Loading**: Verifies successful backend list responses mapping.
  - **Temporary Playback URL**: Verifies retrieval of GCS audio access links.
  - **Rename Safety**: Stubbed deduplication suffix verification returns.
  - **Selective Deletion**: Tests deleting only text files vs deleting both audio and text reports to free space.
  - **Audio Downloading**: Verifies logic for checking local cache vs starting media streams.

- **[RecordAudioViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/dashboard/src/test/java/com/meetloggerv2/ui/audio/viewmodel/RecordAudioViewModelTest.kt)**:
  - **Control Flow States**: Asserts emissions for Idle, Recording, and Saving states.
  - **Worker Dispatch**: Verifies WorkManager calls to upload audio binary files on save.

- **[UploadAudioViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/dashboard/src/test/java/com/meetloggerv2/ui/audio/viewmodel/UploadAudioViewModelTest.kt)**:
  - **Selection Verification**: Verifies lists are correctly retrieved and enqueued to WorkManager with background arguments.

- **[ProfileViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/dashboard/src/test/java/com/meetloggerv2/ui/profile/viewmodel/ProfileViewModelTest.kt)**:
  - **Cache Optimization**: Tests cached profile loading when the last fetch matches the client date.
  - **Sync-on-Miss**: Forces backend fetches when the cache expires or is missing, validating cache updates.
  - **Authentication Signout**: Tests local datastore clears and user state resets.

- **[SettingsViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/dashboard/src/test/java/com/meetloggerv2/ui/profile/viewmodel/SettingsViewModelTest.kt)**:
  - **Preference Updates**: Observes DataStore state changes (theme indices, audio qualities, biometric flags).
  - **Account Deletion**: Verifies delete ticket dispatch and calling Google credentials clear handlers.

- **[SupportViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/dashboard/src/test/java/com/meetloggerv2/ui/profile/viewmodel/SupportViewModelTest.kt)**:
  - **Ticket Creation**: Evaluates support data posts, header authorizations, and network errors.

---

### 3. `feature/details` & `feature/report` Modules

- **[FileDetailsViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/details/src/test/java/com/meetloggerv2/ui/details/viewmodel/FileDetailsViewModelTest.kt)**:
  - **On-Device Translation**: Mocks Google ML Kit translators to test offline translation models downloading states and text translations.
  - **In-place Edits**: Verifies document field saving without triggering cloud reprocessing.
  - **Save-As-New Copying**: Tests new document duplication logic on translation saves.

- **[ReportViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/feature/report/src/test/java/com/meetloggerv2/ui/report/viewmodel/ReportViewModelTest.kt)**:
  - **Collision Rename Handling**: Ensures client ViewModels capture rename collisions and update UI lists immediately.

---

### 4. `data` Module

- **[AudioRepositoryTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/data/src/test/java/com/meetloggerv2/data/repository/AudioRepositoryTest.kt)**:
  - **Storage Handlers**: Verifies upload permissions requests, signed URL requests, and REST calls.

- **[FileRepositoryTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/data/src/test/java/com/meetloggerv2/data/repository/FileRepositoryTest.kt)**:
  - **Synchronization Logic**: Tests logic for reconciling backend lists into local Room databases.

- **[AudioUploadWorkerTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/data/src/test/java/com/meetloggerv2/data/work/AudioUploadWorkerTest.kt)**:
  - **Worker Pipelines**: Verifies full WorkManager execution flows (File checks, History logs updates, signed url fetches, media uploads, and server sync).

---

### 5. `app` & `core` Modules

- **[MainViewModelTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/app/src/test/java/com/meetloggerv2/ui/main/viewmodel/MainViewModelTest.kt)**:
  - **Launch Verification**: Tests checks for existing logged-in accounts on startup.

- **[AuthSessionTest](file:///Users/mutharasan/StudioProjects/MeetLoggerv2/core/src/test/java/com/meetloggerv2/core/session/AuthSessionTest.kt)**:
  - **Session Persistence**: Validates storage of authentication parameters.
