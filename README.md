# Expense Tracker Android App

An Android application that automatically tracks your expenses by parsing bank transaction SMS messages and categorizing them based on user-defined rules.

## Features

- **Automatic SMS Parsing**: Reads bank transaction SMS and extracts transaction details
- **Smart Categorization**: Automatically categorizes expenses based on merchant names using customizable rules
- **24/7 Background Monitoring**: Runs as a foreground service to continuously monitor incoming SMS
- **Dashboard**: View total expenses with time period filters (Today, This Week, This Month)
- **Category Breakdown**: See expenses split by categories with visual breakdown
- **Rules Management**: Create, edit, and manage categorization rules
- **Privacy-First**: All data stored locally on device

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM + Clean Architecture
- **Database**: Room (SQLite)
- **Dependency Injection**: Hilt
- **Background Service**: Foreground Service + BroadcastReceiver

## Requirements

- Android Studio Hedgehog (2023.1.1) or later
- Android SDK 26+ (Android 8.0+)
- Target SDK 34 (Android 14)
- Gradle 8.2+

## Setup Instructions

### 1. Clone or Open Project

Open the project in Android Studio.

### 2. Sync Gradle

Android Studio should automatically sync Gradle dependencies. If not, click:
```
File → Sync Project with Gradle Files
```

### 3. Build the Project

```bash
./gradlew build
```

Or use Android Studio:
```
Build → Make Project
```

### 4. Run on Device/Emulator

**Important**: For SMS functionality to work, you must run on a **physical device** with a SIM card that receives bank SMS.

1. Connect your Android device via USB
2. Enable USB Debugging on your device
3. Click Run (▶️) in Android Studio
4. Select your device

## Permissions

The app requires the following permissions:

- `READ_SMS`: To read bank transaction SMS
- `RECEIVE_SMS`: To receive SMS in real-time
- `POST_NOTIFICATIONS`: To show transaction notifications (Android 13+)
- `RECEIVE_BOOT_COMPLETED`: To restart service after device reboot
- `FOREGROUND_SERVICE`: To run background monitoring service

## Usage

### First Launch

1. Grant SMS and notification permissions when prompted
2. The app will start monitoring for bank SMS automatically
3. Navigate to the Rules screen to view default categorization rules

### Adding Custom Rules

1. Go to the **Rules** tab
2. Tap the **+** button
3. Enter:
   - **Pattern**: Merchant name or keyword (e.g., "ZOMATO", "UBER")
   - **Match Type**: How to match (Contains, Starts With, Ends With, Exact, Regex)
   - **Category**: Which category to assign
4. Tap **Add**

### Viewing Expenses

1. Go to the **Dashboard** tab
2. Select time period (Today, This Week, This Month)
3. View total expenses and category breakdown
4. Tap on a category to see all transactions (coming soon)

## Supported Banks

The SMS parser supports common Indian banks including:

- HDFC Bank
- ICICI Bank
- State Bank of India (SBI)
- Axis Bank
- Kotak Bank
- Punjab National Bank (PNB)
- Bank of India (BOI)
- Canara Bank
- Union Bank

Additional banks can be added by updating the `TransactionParser.kt` file.

## Default Categories

- 🍔 **Food**: Zomato, Swiggy, Dominos, McDonald's, KFC, etc.
- 🚗 **Transport**: Uber, Ola, Rapido, Petrol, Fuel, Parking
- 🛍️ **Shopping**: Amazon, Flipkart, Myntra, Ajio, Meesho
- 💡 **Bills**: Electricity, Water, Internet, Mobile, Recharge
- 🎬 **Entertainment**: Netflix, Prime, Hotstar, Spotify, BookMyShow
- ⚕️ **Health**: Pharmacy, Hospital, Clinic, Apollo, MedPlus
- 📦 **Others**: Uncategorized transactions

## Project Structure

```
app/src/main/java/com/expensetracker/
├── data/
│   ├── local/
│   │   ├── entity/          # Room entities
│   │   ├── dao/             # Data Access Objects
│   │   └── database/        # Database setup
│   └── repository/          # Repository implementations
├── domain/
│   └── usecase/             # Business logic (CategorizationEngine)
├── presentation/
│   ├── dashboard/           # Dashboard screen & ViewModel
│   ├── rules/               # Rules screen & ViewModel
│   ├── settings/            # Settings screen
│   └── theme/               # Compose theme
├── service/
│   ├── SmsReceiver.kt       # SMS BroadcastReceiver
│   ├── TransactionService.kt # Foreground service
│   ├── BootReceiver.kt      # Boot receiver
│   └── parser/              # SMS parsing logic
├── util/                    # Utility classes
├── di/                      # Hilt modules
├── MainActivity.kt
└── ExpenseTrackerApp.kt
```

## Testing

### Testing SMS Parsing (Emulator)

You can test SMS parsing on an emulator using ADB:

```bash
adb emu sms send 5556 "Rs 450.00 debited from A/c XX1234 on 22-12-25 at ZOMATO. Avl Bal: Rs 5000.00"
```

### Testing on Physical Device

1. Install the app on your device
2. Ensure SMS permissions are granted
3. Wait for a real bank transaction SMS
4. Check the Dashboard to see if the transaction was captured

## Troubleshooting

### App not receiving SMS

1. Check if SMS permissions are granted in Settings
2. Ensure the foreground service notification is visible
3. Disable battery optimization for the app
4. Restart the app

### Transactions not categorized correctly

1. Go to Rules screen
2. Check if there's a matching rule for the merchant
3. Add a new rule if needed
4. Ensure the rule is active (toggle switch is ON)

### Service stops after some time

1. Disable battery optimization for the app
2. Add the app to the "Never sleeping apps" list (Samsung devices)
3. Enable "Autostart" permission (Xiaomi, Oppo, Vivo devices)

## Known Limitations

- Only processes DEBIT transactions (credits are ignored)
- Requires SMS to be in a recognizable format
- May not work with all banks (parser can be extended)
- Category details screen is not yet implemented
- No data export/backup feature yet

## Future Enhancements

- Category details screen with transaction list
- Data export to CSV
- Charts and analytics
- Budget tracking
- Recurring transaction detection
- Multi-account support
- Cloud backup

## License

This project is for educational purposes.

## Privacy

- All data is stored locally on your device
- No data is sent to any server
- SMS messages are only read from known bank senders
- You can delete all data anytime from the app
