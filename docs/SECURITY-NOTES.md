# Security notes and debt register

## Open risks

1. **Licence keys use a shared secret** (`LicenseKeys.SECRET`). Offline validation means the
   secret must ship inside the app, so a determined user can mint keys.
   *Upgrade path:* sign licence codes with an Ed25519 private key on the sales side and embed only
   the **public** key in the app. Keep accepting legacy `PLAN-YYYYMMDD-CHECKSUM` codes for a
   transition period so existing customers are unaffected.
2. **Signing keystore is committed** (`keys/teacher-upload.keystore`) with its password in
   `teacher-app/build.gradle.kts`. Anyone with repo access can sign an APK that Android accepts as
   a legitimate update.
   *Ready now:* the build reads `SRS_KEYSTORE_B64` / `SRS_KEYSTORE_PASSWORD` / `SRS_KEY_ALIAS` /
   `SRS_KEY_PASSWORD` from the environment first. Next step is to add those as GitHub secrets,
   delete `keys/` from the repo, and add a workflow step that writes the keystore from the secret.
   *Rotation caveat:* a new key means installed phones must uninstall/reinstall, so rotate with a
   planned migration (export marks → new install → import), not as a hotfix.
3. **MSI is unsigned** — Windows SmartScreen warns on first run. Needs a code-signing certificate
   (paid); until then, publish the SHA-256 of each MSI next to the download.

## Resolved

- `version.json` in the repo drifted from the release assets, so console users hit 404s on
  `/releases/latest/download/...`. The manifest is now part of the release checklist.
- 38 empty `catch` blocks silently swallowed failures; they now print the stack trace.
