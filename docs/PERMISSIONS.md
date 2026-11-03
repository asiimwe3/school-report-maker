# Teacher app permissions (what is actually shipped)

The app works fully offline; these permissions exist only for optional features.
This file is the source of truth — keep it in sync with `teacher-app/src/main/AndroidManifest.xml`.

| Permission | Why | What happens if denied |
|---|---|---|
| `INTERNET` | Join/sync with the school console, in-app update check | App still works fully offline; joining and updates unavailable |
| `CAMERA` | Capture/scan documents and photos for attachments | Manual entry only |
| `REQUEST_INSTALL_PACKAGES` | In-app updater installs the new APK | User must install updates manually |

**Privacy position:** no analytics SDK, no advertising SDK. Data leaves the device only when a
teacher explicitly submits marks to their school, and only to the school's own console.

> Earlier READMEs claimed the app shipped *without* the internet permission. That stopped being
> true when school sync was added. Any store listing, privacy policy or sales page must describe
> the permissions above.
