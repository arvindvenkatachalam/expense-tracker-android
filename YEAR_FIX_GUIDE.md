# Fix Year Parsing - Push and Test Guide

## What Was Fixed

**Problem:** Date "01/12/25" was being parsed as year **0025** instead of **2025**

**Solution:** Set the 2-digit year pivot to year 2000, so:
- Years 00-99 map to 2000-2099
- "25" becomes **2025** ✅
- "99" becomes **2099** ✅

## Changes Made

1. **HdfcStatementParser.kt** (lines 30-36):
   - Fixed the `SimpleDateFormat("dd/MM/yy")` to correctly interpret 2-digit years
   - Set pivot year to 2000

2. **Added logging** (line 138):
   - Shows parsed dates in logcat for verification
   - Example: "Parsed date '01/12/25' as: Sun Dec 01 00:00:00 IST 2025"

## Push to GitHub

### Quick Commands:
```powershell
cd C:\Users\VArvind\Documents\summa

git add app/src/main/java/com/expensetracker/domain/parser/HdfcStatementParser.kt

git commit -m "Fix 2-digit year parsing: 25 -> 2025 (not 0025)"

git push origin main
```

## After Pushing

1. **Wait for build** - Check GitHub Actions
2. **Download APK** - Get the latest build
3. **Test import:**
   - Upload your PDF
   - Check the transaction list - dates should show **2025** not **0025**
   - Click Import
   - Go to Dashboard
   - Transactions should appear in "This Month"!

## Verification

The Toast message will still show:
- "Imported X transactions. Total in DB: Y"

But now when you look at the transaction list BEFORE importing, you should see dates like:
- "01 Dec 2025" ✅ (not "01 Dec 0025" ❌)

## If It Still Doesn't Work

Check the logcat for lines like:
```
Parsed date '01/12/25' as: Sun Dec 01 00:00:00 IST 2025
```

This will confirm the year is being parsed correctly.
