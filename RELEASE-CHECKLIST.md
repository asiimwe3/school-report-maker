# Release Checklist — v2.2.25

## Included in this release

- Admin console and teacher app UI refresh with clearer navigation, context, and quick actions.
- Admin student editing, archive, and confirmed delete controls.
- Scroll-safe desktop page layout for long screens.
- Separate School fees, Boarding fees, and Bursary ledgers.
- Independent ledger balances in admin, Word reports, and the teacher app.
- Detailed fee-collection operating guide.
- GitHub Release workflow that uploads the Windows MSI and teacher APK.

## Required validation before publishing

1. Run `gradle :core:test :desktop-admin:build :teacher-app:assembleDebug` on a machine with JDK 17 and Gradle 8.9.
2. Install the MSI on a clean Windows PC and complete setup, student editing, fee entry, report generation, backup, and restore.
3. Install the APK on an Android 8+ device; confirm that teacher fee status matches the admin ledger after importing a fresh configuration.
4. Confirm an old school-data.json opens successfully. Existing fee records must appear under School fees.
5. Create a class with all three fee ledgers; verify that a payment in one ledger does not change the other two balances.
6. Verify generated report cards show a separate section for each active ledger.

## Android distribution note

The release workflow currently attaches an unsigned debug APK so field testers can install it. For public Play Store distribution, create and protect a private Android release keystore, configure signing outside source control, build `assembleRelease`, and upload the signed release APK/AAB instead. Never commit a keystore or its passwords.

## Publishing

After validation, commit all changes, push `main`, and create/push tag `v2.2.25`. The release workflow will build and attach the MSI and APK to that GitHub release.

## After publishing a release (do not skip)

1. Open https://github.com/asiimwe3/school-report-maker/releases/latest and confirm the `.msi` and
   `.apk` assets are listed with the **exact filenames** written in `version.json`.
2. Open every URL in `version.json` in a browser — each must download, not 404.
3. Confirm `versionName` / `versionCode` in `teacher-app/build.gradle.kts` and
   `consoleVersion` in `desktop-admin/build.gradle.kts` match `version.json`.

## Before pushing a version tag

The release workflow attaches the MSI, APK and `version.json` to an **existing** GitHub release.
A pushed tag on its own does not create one, and every attach step then fails with
"release not found". Either create it first (`gh release create vX.Y.Z --title vX.Y.Z`) or create
it on github.com, then push the tag.
