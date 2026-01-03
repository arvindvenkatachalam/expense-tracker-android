@echo off
echo Pushing syntax fixes to GitHub...
echo.

cd /d "C:\Users\VArvind\Documents\summa"

echo Adding fixed files...
git add app/src/main/java/com/expensetracker/presentation/pdfimport/PdfImportViewModel.kt
git add app/src/main/java/com/expensetracker/presentation/pdfimport/PdfImportScreen.kt
git add app/src/main/java/com/expensetracker/presentation/dashboard/DashboardScreen.kt

echo.
echo Committing changes...
git commit -m "Fix syntax errors from browser automation - correct string escaping"

echo.
echo Pushing to GitHub...
git push origin main

echo.
echo Done! Check GitHub Actions for build status.
pause
