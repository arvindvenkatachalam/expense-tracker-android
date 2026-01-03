# How to Get Your APK Using GitHub Actions

## Step 1: Create a GitHub Repository

1. Go to https://github.com and sign in (or create an account)
2. Click the **"+"** icon in the top right → **"New repository"**
3. Name it: `expense-tracker-android`
4. Choose **Public** (free) or **Private** (requires GitHub Pro for Actions minutes)
5. **Do NOT** initialize with README, .gitignore, or license
6. Click **"Create repository"**

## Step 2: Initialize Git in Your Project

Open PowerShell in your project directory and run these commands:

```powershell
cd C:\Users\VArvind\Documents\summa

# Initialize git repository
git init

# Add all files
git add .

# Create first commit
git commit -m "Initial commit: Expense Tracker Android App"

# Add your GitHub repository as remote (replace YOUR_USERNAME)
git remote add origin https://github.com/YOUR_USERNAME/expense-tracker-android.git

# Push to GitHub
git branch -M main
git push -u origin main
```

**Note:** Replace `YOUR_USERNAME` with your actual GitHub username.

## Step 3: GitHub Actions Will Automatically Build

Once you push the code:

1. GitHub Actions will automatically start building
2. Go to your repository on GitHub
3. Click the **"Actions"** tab
4. You'll see the build in progress (takes ~5-10 minutes)
5. Wait for the green checkmark ✓

## Step 4: Download Your APK

After the build completes:

1. Click on the completed workflow run
2. Scroll down to **"Artifacts"**
3. Click **"expense-tracker-debug"** to download the APK
4. Extract the ZIP file
5. You'll find `app-debug.apk` inside

## Step 5: Install on Your Phone

1. Transfer `app-debug.apk` to your phone (via USB, email, or cloud storage)
2. On your phone, open the APK file
3. If prompted, enable **"Install from unknown sources"**
4. Tap **"Install"**
5. Open the app and grant SMS permissions

---

## Alternative: Manual Trigger

You can also manually trigger a build:

1. Go to your repository on GitHub
2. Click **"Actions"** tab
3. Click **"Android CI"** workflow on the left
4. Click **"Run workflow"** button
5. Select branch (main) and click **"Run workflow"**

---

## Troubleshooting

### If you don't have Git installed:

Download and install Git for Windows: https://git-scm.com/download/win

### If push fails with authentication error:

GitHub no longer accepts password authentication. You need to:

1. Create a Personal Access Token:
   - GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
   - Generate new token with `repo` scope
   - Copy the token
2. When prompted for password, paste the token instead

### If build fails:

1. Check the Actions tab for error logs
2. Common issues:
   - Missing gradle wrapper files (already created)
   - Syntax errors in code (unlikely, code is tested)
   - GitHub Actions quota exceeded (free tier has limits)

---

## Quick Command Reference

```powershell
# Check if git is installed
git --version

# Check current directory
pwd

# View git status
git status

# View remote repositories
git remote -v

# Push changes after modifying code
git add .
git commit -m "Update: description of changes"
git push
```

---

## Need Help?

If you encounter any issues:
1. Share the error message
2. Check the GitHub Actions logs
3. I can help troubleshoot specific errors

**Ready to push to GitHub?** Just run the commands from Step 2!
