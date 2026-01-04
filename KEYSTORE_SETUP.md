# Release Keystore Setup - Complete Guide

## What Was Done

I've configured your project for consistent app signing across local and GitHub builds. Here's what changed:

### Files Modified:
1. **app/build.gradle.kts** - Added signing configuration
2. **.github/workflows/build.yml** - Updated to use keystore from secrets
3. **.gitignore** - Added keystore.properties protection
4. **Version bumped** - versionCode: 1 → 2, versionName: 1.0 → 1.1

### Files Created:
1. **generate_keystore.bat** - Script to generate keystore
2. **keystore.properties.template** - Template for your credentials

---

## Setup Steps (Do These Now)

### Step 1: Generate the Keystore

Run this command:
```powershell
.\generate_keystore.bat
```

**You will be prompted for:**
- Keystore password (choose a strong password and **SAVE IT**)
- Key password (can be same as keystore password)
- Your name
- Other details (can skip by pressing Enter)

**CRITICAL:** Save both passwords securely! You'll need them forever.

### Step 2: Create keystore.properties

Copy the template and fill in your passwords:
```powershell
Copy-Item keystore.properties.template keystore.properties
```

Then edit `keystore.properties` and replace:
- `YOUR_KEYSTORE_PASSWORD_HERE` with your keystore password
- `YOUR_KEY_PASSWORD_HERE` with your key password

### Step 3: Set Up GitHub Secrets

1. **Get the base64 encoded keystore:**
   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("app\release-keystore.jks")) | Set-Clipboard
   ```
   This copies the encoded keystore to your clipboard.

2. **Go to GitHub:**
   - Open: https://github.com/arvindvenkatachalam/expense-tracker-android/settings/secrets/actions
   - Click "New repository secret"

3. **Add these 4 secrets:**

   | Secret Name | Value |
   |-------------|-------|
   | `KEYSTORE_BASE64` | Paste from clipboard (from step 1) |
   | `KEYSTORE_PASSWORD` | Your keystore password |
   | `KEY_PASSWORD` | Your key password |
   | `KEY_ALIAS` | `expense-tracker-key` |

---

## Step 4: Test Locally (Optional)

If you have Java/Android Studio set up:
```powershell
.\gradlew.bat assembleDebug
```

The APK will be at: `app\build\outputs\apk\debug\app-debug.apk`

---

## Step 5: Push to GitHub

```powershell
git add .
git commit -m "Configure release keystore for consistent app signing"
git push origin main
```

GitHub Actions will build a signed APK that can update your existing app!

---

## Step 6: Install the New APK

1. Wait for GitHub Actions to complete
2. Download the APK from GitHub Actions artifacts
3. **Uninstall your current app** (one-time only, sorry - data will be lost)
4. Install the new APK

**From now on**, all future APKs from GitHub will have the same signature and can update without uninstalling!

---

## Important Notes

✅ **Never commit** `keystore.properties` or `release-keystore.jks` to Git
✅ **Backup** your keystore file somewhere safe (Google Drive, etc.)
✅ **Save** your passwords in a password manager
❌ **If you lose** the keystore or passwords, you can never update the app again

---

## Troubleshooting

**Q: generate_keystore.bat fails?**
A: You need Java installed. Install Android Studio or Java JDK 17.

**Q: GitHub Actions build fails?**
A: Make sure all 4 secrets are set correctly in GitHub.

**Q: Still getting "package conflicts"?**
A: You must uninstall the old app first (one time only). Future updates will work.

---

## What Happens Next

1. ✅ Local builds use your keystore
2. ✅ GitHub Actions builds use the same keystore (from secrets)
3. ✅ All APKs have the same signature
4. ✅ App updates work seamlessly without uninstalling
5. ✅ Your data is preserved across updates

---

**Ready to proceed? Run the steps above and let me know if you need help!**
