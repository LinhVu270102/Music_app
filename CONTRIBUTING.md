# Contributing

Thank you for your interest in contributing to **Orange Music**.

Orange Music is a graduation project built with Android Kotlin, Firebase, Media3 ExoPlayer, and a Node.js SoundCloud server. Contributions should focus on improving code quality, fixing bugs, improving documentation, and keeping the project stable.

## Project Scope

This project includes:

- Android app source code
- Firebase integration
- Music playback features
- Playlist, like, follow, history, and notification features
- User upload and admin moderation features
- Node.js server for third-party music API integration

Please keep changes aligned with the purpose of the project.

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/LinhVu270102/Music_app.git
cd Music_app
```

### 2. Open the Android project

Open the project with Android Studio and sync Gradle.

### 3. Configure Firebase

Place your Firebase configuration file here:

```text
app/google-services.json
```

Do not commit private keys, service account files, keystores, or environment files.

### 4. Configure the Node.js server

```bash
cd soundcloud-server
npm install
```

Create a local `.env` file based on `.env.example` if available.

```env
SOUNDCLOUD_CLIENT_ID=your_client_id
SOUNDCLOUD_CLIENT_SECRET=your_client_secret
PORT=3000
```

Run the server:

```bash
npm start
```

## Branch Naming

Use clear branch names:

```text
feature/player-follow-sync
fix/search-playlist-result
refactor/home-viewmodel
docs/update-readme
```

## Commit Guidelines

Write short and clear commit messages.

Good examples:

```text
Fix player follow state sync
Add notification click navigation
Refactor search result rendering
Update Firebase security rules
```

Avoid unclear messages:

```text
update
fix
final
test
```

## Code Style

For Android Kotlin code:

- Follow MVVM structure
- Keep Fragment focused on UI logic
- Put data operations inside Repository classes
- Put UI state logic inside ViewModel
- Avoid hard-coded text in Kotlin/XML
- Use `strings.xml` for user-facing text
- Keep functions small and readable
- Follow the project file order where possible:
  - constants / companion object
  - binding, ViewModel, adapter, state
  - lifecycle
  - setup UI
  - observe ViewModel
  - render UI
  - navigation, dialog, helper
  - cleanup

## Before Submitting Changes

Please check:

- The app builds successfully
- No unresolved references remain
- Login/register still works
- Music playback still works
- Like/follow state updates correctly if related code changed
- Playlist features still work if related code changed
- No secrets are committed
- `.env`, `node_modules`, `local.properties`, keystore files, and service account files are ignored

## Files That Must Not Be Committed

Do not commit:

```text
.env
*.env
soundcloud-server/.env
node_modules/
soundcloud-server/node_modules/
local.properties
serviceAccountKey.json
*.jks
*.keystore
.gradle/
.kotlin/
.idea/
build/
app/build/
```

## Pull Requests

When opening a pull request, include:

- What was changed
- Why it was changed
- How it was tested
- Screenshots or screen recordings for UI changes if possible

Example:

```md
## Summary

Fix follow button state between Mini Player and Player Fragment.

## Changes

- Added follow state observer
- Updated follow button rendering
- Reused repository follow logic

## Testing

- Tested follow/unfollow from Mini Player
- Tested follow/unfollow from Player Fragment
- Verified state stays synchronized
```

## Issues

When reporting a bug, include:

- Device or emulator information
- Android version
- Steps to reproduce
- Expected result
- Actual result
- Screenshot, screen recording, or logcat if available

## Code of Conduct

Please follow the project [Code of Conduct](CODE_OF_CONDUCT.md).

## License

By contributing to this project, you agree that your contributions will be included under the same license as the project.
