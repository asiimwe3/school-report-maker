# SRS v2.2.0 — Cloud Setup (Admin Console)

## What's new
- Mandatory setup wizard on first launch: School Profile → Online Account → Security PIN → Licence → Dashboard
- Cloud account (Supabase): automatic online backup after every save, one-click restore
- Teacher school invite code (8 characters) shown in the wizard and under SYSTEM → Cloud & Online
- "Pull Teacher Marks" — teachers' submissions come from the cloud automatically
- Offline mode still available (choose "Set up later" in the online step)

## To enable the cloud (one time, ~5 minutes)
1. Go to https://supabase.com → New project (free plan is fine)
2. SQL Editor → paste docs/supabase-setup.sql → Run
3. Settings → API → copy the Project URL and the anon public key
4. In the setup wizard, enter them in the "Cloud server URL" / "Server key" fields

(For the next build these can be pre-filled — send me the URL + anon key and I'll bake them in.)

## Windows MSI
Push this source to GitHub and tag `v2.2.0` — the workflow builds the MSI + Teacher APK automatically.

## Teacher app (online login) — next update (1.8.0)
Teachers will sign up in the app with your school invite code; not yet in this release.

## CLOUD STATUS: not yet configured
- Server: https://eiyexnuhqdscomilwpqg.supabase.co (Supabase, free tier)
- The srs_* tables are already installed and security-tested (signup, school creation,
  teacher invite-code linking, backups and marks submission all verified end-to-end).
- Email confirmation is disabled on this project (required for the free email limit;
  it means teachers can sign up instantly without waiting for an email).
- Nothing to configure: just run the console and create your admin account in the wizard.
- Note: SRS cloud lives alongside the Tropical Gardens Hotel data in the same project
  (separate srs_* tables — they never mix). If you later create a dedicated project,
  send me the new URL + anon key and I'll move it in one update.
