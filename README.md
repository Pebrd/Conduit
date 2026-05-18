<p align="center">
  <img src="conduit_logo.svg" width="250" alt="Conduit Logo">
</p>

<h1 align="center">Conduit 🎵</h1>

<p align="center">
  <b>A powerful Kotlin Multiplatform (KMP) application to seamlessly synchronize music libraries and playlists between Spotify and Tidal.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-Multiplatform-4285F4?logo=jetpackcompose" alt="Compose Multiplatform">
  <img src="https://img.shields.io/badge/Platform-Android%20%7C%20Desktop-lightgrey" alt="Platforms">
  <img src="https://img.shields.io/badge/License-MIT-green" alt="License">
</p>

---

## 📖 Overview

**Conduit** is designed for audiophiles and music lovers who use multiple streaming services. By connecting to the Spotify and Tidal APIs, Conduit allows you to migrate, sync, and manage your playlists across platforms effortlessly. 

Built with **Compose Multiplatform**, the application provides a native, high-performance, and beautiful UI experience across both Android and Desktop environments.

## ✨ Features

- **🔄 Bi-directional Synchronization:** Transfer playlists from Spotify to Tidal, and vice-versa.
- **🔐 Secure Authentication:** Implements robust OAuth 2.0 flows. Uses Device Authorization Grant for Tidal and standard PKCE auth for Spotify, securely storing tokens locally.
- **📱 Cross-Platform:** Write once, run anywhere. Native support for Android and Desktop (macOS, Windows, Linux).
- **🎨 Premium UI/UX:** A sleek, responsive, and modern interface built entirely in Compose.
- **🚀 Background Polling:** Non-blocking authorization polling and sync mechanisms to ensure a smooth user experience.

## 🎨 Design System & Aesthetics (Nox)

Conduit's premium interface is dynamically synchronized with the **Nox Design System** (defined in [pebrd/nox](https://github.com/pebrd/nox)). The application is designed to follow a high-contrast monochromatic brutalist style:

- **⚫ AMOLED Absolute Black**: Sleek energy-saving dark palette (`ColorBgBase`, `ColorBgSurface`, `ColorBgSurface2` mapped straight from `NoxTokens`).
- **📐 Flat Brutalist Shapes**: No rounded corners (`0.dp` border radius) for a technical, command-line inspired layout.
- **🅰️ Custom Google Fonts**: Fully integrated with **IBM Plex Sans** (Light, Regular, SemiBold) for displays and bodies, and **IBM Plex Mono** (Light, Regular) for tags, track IDs, ISRCs, scores, and logs.

### Dynamic Auto-Updating Tokens

The design system auto-updates from GitHub on every compilation cycle:
- **Gradle Task Automation**: The custom task `:composeApp:updateDesignTokens` is executed automatically before Kotlin compilation or resource generation. It pulls the latest commits from the `pebrd/nox` repository and copies `NoxTokens.kt` straight into our common Compose module.
- **Manual Synchronization**: You can also trigger an on-demand update of the design tokens directly from your terminal using:
  ```bash
  ./update_tokens.sh
  ```

## 🛠️ Technology Stack

- **[Kotlin Multiplatform (KMP)](https://kotlinlang.org/docs/multiplatform.html):** Core business logic and architecture sharing.
- **[Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/):** Declarative UI framework.
- **[Ktor](https://ktor.io/):** For efficient asynchronous HTTP networking and API communication.
- **Coroutines & Flow:** For reactive programming and concurrency.

## 🚀 Getting Started

### Prerequisites

- [Java Development Kit (JDK) 17](https://adoptium.net/) or higher.
- [Android Studio](https://developer.android.com/studio) (Koala or newer recommended) or [IntelliJ IDEA](https://www.jetbrains.com/idea/).

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/Conduit.git
cd Conduit
```

### 2. API Credentials Setup

For security reasons, Conduit **does not** hardcode API keys. You must provide your own credentials via the app's settings.

1. **Spotify:** Go to the [Spotify Developer Dashboard](https://developer.spotify.com/dashboard), create an app, and obtain your `Client ID` and `Client Secret`. Set the Redirect URI to `conduit://callback`.
2. **Tidal:** Go to the [Tidal Developer Dashboard](https://developer.tidal.com/), create an app, and obtain your credentials.

> *Note: Credentials inputted in the app are safely saved to your device's local secure storage and are never tracked by Git.*

### 3. Run the Application

#### Android
Open the project in Android Studio, select the `composeApp` run configuration targeting your Android Emulator or physical device, and click **Run**.
Alternatively, via terminal:
- **Debug Build** (uses package `com.conduit.debug` allowing co-existence with release):
  ```bash
  ./gradlew :composeApp:installDebug
  ```
- **Release Build** (uses package `com.conduit` and is signed automatically with `conduit.keystore`):
  ```bash
  ./gradlew :composeApp:assembleRelease
  ```

#### Desktop (macOS, Windows, Linux)
Run the following Gradle task to start the desktop app:
```bash
./gradlew :composeApp:run
```

## 📂 Project Structure

```text
Conduit/
├── composeApp/
│   ├── src/commonMain/   # Shared business logic, ViewModels, UI and API clients
│   ├── src/androidMain/  # Android specific implementations (OAuth handling, etc)
│   └── src/desktopMain/  # Desktop specific implementations
├── gradle/               # Gradle wrapper and configurations
└── settings.gradle.kts   # Project build configuration
```

## 🔒 Security

We take security seriously. 
- API keys, secrets, and User tokens are strictly handled locally. 
- `local.properties`, keystores (`.jks`, `.keystore`), and `keystore_password.txt` are strictly ignored by `.gitignore`. 
- No credentials will be accidentally uploaded to this repository.

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! 
Feel free to check the [issues page](../../issues).

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
