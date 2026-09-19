# Key rotation plan — teacher app upload signing

**Date:** 2026-09-19 · **Status:** MANDATORY before any further release

## What happened

The teacher-app upload keystore (`keys/teacher-upload.keystore`) was committed
to this repository together with its store and key passwords hard-coded in
`teacher-app/build.gradle.kts`. Anyone with repository read access could sign
apps as this project. The old key is treated as **compromised**.

## Actions taken

1. `keys/teacher-upload.keystore` removed from the working tree and from all
   future commits.
2. `build.gradle.kts` now reads signing material exclusively from environment
   variables (`SRS_UPLOAD_STORE_BASE64`, `SRS_UPLOAD_STORE_PASSWORD`,
   `SRS_UPLOAD_KEY_ALIAS`, `SRS_UPLOAD_KEY_PASSWORD`). There is no code path
   that silently falls back to a debug key — a release build without these
   variables fails instead of producing a mis-signed artifact.
3. No further tagged release is published until the rotation steps below are
   complete.

## Rotation procedure (owner, before the next release)

1. Generate a NEW upload key locally, offline:
   `keytool -genkeypair -v -keystore teacher-upload-v2.jks -alias teacher-upload -keyalg RSA -keysize 4096 -validity 10000`
2. Store the JKS and passwords ONLY in a secret store (GitHub Actions secrets
   or a vault). Never in this repo, email, or chat.
3. Add repository secrets: `SRS_UPLOAD_STORE_BASE64` (base64 of the JKS),
   `SRS_UPLOAD_STORE_PASSWORD`, `SRS_UPLOAD_KEY_ALIAS`, `SRS_UPLOAD_KEY_PASSWORD`.
4. Android treats a new signature as a different application: teachers must
   uninstall the old app and install the new build once. Announce this before
   shipping. Consider Play App Signing for long-term management.
5. After the new key is in production, purge the old keystore from Git
   history with `git filter-repo --path keys/teacher-upload.keystore
   --invert-paths` and force-push. Rotation is the real fix; history cleanup
   afterwards is defence in depth.

## SaaS note

The SaaS platform (saas/) signs nothing with repository-held keys. Report
PDFs/DOCX are generated server-side; any document-signing keys live in the
server secret store only.
