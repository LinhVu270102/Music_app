# Music App code structure

## Folder responsibilities

```text
app/src/main/kotlin/com/example/music_app/
- data/
  - local/       On-device state such as search history
  - model/       Plain data models: Song, Playlist, User, Comment
  - remote/      Firestore and SoundCloud data sources
  - repository/  Feature-oriented data access and permission checks
- domain/usecase/  Business actions that combine repositories
- player/
  - PlayerManager.kt           ExoPlayer lifecycle and public playback commands
  - PlaybackQueue.kt           In-memory queue state and selection
  - PlaybackSongSelector.kt    Pure fallback and random-tail selection rules
  - PlaybackMediaItemFactory.kt  Song-to-Media3 mapping
  - PlaybackHistoryRecorder.kt Persists recently played songs off the UI thread
  - state/            State shared by full and mini players
- main/
  - FooterNavigationController.kt  Footer tab selection and visibility
  - MiniPlayerSwipeController.kt  Two-layer mini-player swipe animation
  - MiniPlayerPresentationController.kt  Mini-player view and social-button rendering
- service/            Android foreground playback service
- ui/<feature>/       Fragment or Activity, ViewModel, and Adapter per feature
```

## Data flow

```text
Fragment / Activity -> ViewModel -> UseCase (when needed) -> Repository -> RemoteDataSource
```

Remote data sources own the Firestore query and write details. Repositories combine those
sources with authentication and feature permission checks. ViewModels expose screen state;
Fragments render that state and dispatch user actions.

## Remote data sources

| Source | Responsibility |
| --- | --- |
| `SongRemoteDataSource` | Songs, moderation fields, and listening history |
| `PlaylistRemoteDataSource` | Owned, public, and liked playlists |
| `CommentRemoteDataSource` | Comments and comment moderation queries |
| `SocialRemoteDataSource` | Song likes and user follow relationships |
| `NotificationRemoteDataSource` | In-app notifications and read state |
| `UserRemoteDataSource` | Public profiles and roles |
| `ReportRemoteDataSource` | Reports and moderation status |

`data/remote/soundcloud/SoundCloudSession` centralizes the current Firebase user
and proxy-response validation for the SoundCloud repositories.

## Conventions

1. Fragment and Activity only bind views, observe state, navigate, and delegate complex dialogs or gestures to a focused controller.
2. ViewModel owns screen state and calls a use case or repository.
3. UseCase contains a business action spanning more than one repository or source.
4. Repository owns feature rules; RemoteDataSource owns Firebase or external API access.
5. Adapter only renders data and emits user clicks. Use `ListAdapter` with `DiffUtil` for lists.
6. Keep members in this order: dependencies, state, public actions, private helpers, cleanup.

## Feature map

| Feature | UI folder | Main data/business files |
| --- | --- | --- |
| Authentication | `ui/auth` | `AuthRepository`, `UserRepository` |
| Home and discovery | `ui/home` | `SoundCloudRepository`, `SongRepository` |
| Search | `ui/search` | `SearchRepository` |
| Full and mini player | `ui/player`, `player`, `main` | `PlayerManager`, `PlaybackQueue`, `MiniPlayerSwipeController`, `PlayerInteractionState` |
| Library and likes | `ui/library`, `ui/yourlikes` | `SongRepository`, `SocialRepository`, `PlaylistRepository` |
| Playlists | `ui/playlists` | `PlaylistRepository`, `SoundCloudPlaylistRepository`, `PlaylistUseCase` |
| Comments | `ui/comment` | `CommentRepository` (Firebase + SoundCloud facade), `SoundCloudCommentRepository`, `CommentDialogController` |
| Upload music | `ui/yourupload` | `UploadMusicRepository`, `UploadMusicUseCase` |
| Profile and following | `ui/profile`, `ui/following` | `UserRepository`, `SocialRepository` |
| Notifications | `ui/notification` | `NotificationRepository` |
| Moderation | `ui/admin` | `AdminRepository`, `ReportRemoteDataSource` |
