# Orange Music

![Android](https://img.shields.io/badge/platform-Android-brightgreen)
![Kotlin](https://img.shields.io/badge/language-Kotlin-blue)
![MVVM](https://img.shields.io/badge/architecture-MVVM-orange)
![Firebase](https://img.shields.io/badge/backend-Firebase-yellow)

Orange Music is an Android music streaming app built with **Kotlin**, **Firebase**, **Media3 ExoPlayer**, and a **Node.js SoundCloud server**. It supports music playback, search, playlists, likes, follows, listening history, user uploads, and admin moderation.

> Graduation project — Software Engineering.

## Features

- Firebase Authentication for login and registration
- Music playback with Media3 ExoPlayer
- Full player, mini player, next/previous playback
- Search tracks, users, and playlists
- Like songs and follow users/artists
- Create and manage playlists
- Recently played and listening history
- Upload songs and manage uploaded content
- Admin moderation for songs, reports, and comments
- SoundCloud integration through a local Node.js server

## Tech Stack

| Area | Technology |
| --- | --- |
| Mobile | Kotlin, Android XML, ViewBinding |
| Architecture | MVVM, Repository Pattern |
| Playback | Media3 ExoPlayer, Foreground Service |
| Backend | Firebase Auth, Cloud Firestore, Firebase Storage |
| Server | Node.js, Express |
| External API | SoundCloud API |

## Project Structure

```text
Music_app/
├── app/src/main/kotlin/com/example/music_app/
│   ├── base/          # Base classes
│   ├── data/          # Models, repositories, remote data sources
│   ├── main/          # MainActivity and app navigation
│   ├── player/        # PlayerManager and playback state
│   ├── service/       # Music foreground service
│   └── ui/            # App screens and feature modules
├── soundcloud-server/ # Node.js server for SoundCloud integration
├── firestore.rules    # Firestore security rules
├── storage.rules      # Firebase Storage security rules
└── CODE_STRUCTURE.md  # Code organization notes
```

## Getting Started

### Prerequisites

- Android Studio
- JDK 17 or compatible Android Gradle setup
- Firebase project
- Node.js and npm, only required for the SoundCloud server

### Android App

```bash
git clone https://github.com/LinhVu270102/Music_app.git
cd Music_app
```

Place your Firebase config file at:

```text
app/google-services.json
```

Then open the project in Android Studio, sync Gradle, and run the app on an emulator or physical device.

### SoundCloud Server

```bash
cd soundcloud-server
npm install
npm start
```

Create `soundcloud-server/.env` before starting the server:

```env
SOUNDCLOUD_CLIENT_ID=your_client_id
SOUNDCLOUD_CLIENT_SECRET=your_client_secret
PORT=3000
```

For Android Emulator, use:

```text
http://10.0.2.2:3000
```

## Firebase Rules

The project includes Firebase rule files:

```text
firestore.rules
storage.rules
```

These files should be kept in version control so database and storage access policies can be reviewed with the source code.

## Security Notes

Do not commit local files, generated folders, or secrets:

```text
.env
*.env
local.properties
node_modules/
.gradle/
.kotlin/
build/
app/build/
serviceAccountKey.json
*.jks
*.keystore
```

The following files should be committed:

```text
README.md
CODE_STRUCTURE.md
firestore.rules
storage.rules
soundcloud-server/package.json
soundcloud-server/package-lock.json
```

## Limitations

- SoundCloud tracks depend on SoundCloud API availability and rate limits.
- Stream URLs may expire and need to be refreshed by the server.
- The SoundCloud server must be running when testing SoundCloud-based playback locally.
- Firebase rules must be configured correctly before production use.

## Documentation

- [Code Structure](CODE_STRUCTURE.md)
- [Contributing Guide](CONTRIBUTING.md)
- [Security Policy](SECURITY.md)
- [Code of Conduct](CODE_OF_CONDUCT.md)
