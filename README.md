# Theft Guard (MVP)

SMS-triggered anti-theft app for Android 8.1+ (minSdk 27). Sideload only (Google Play does not allow this SMS use).

## Run it
1. On a computer, install Android Studio and open this `TheftGuard` folder. Let Gradle sync; it downloads Gradle 8.7 and project dependencies.
2. Build a debug APK from Android Studio with **Build > Build Bundle(s) / APK(s) > Build APK(s)**, or run `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`) from this folder.
3. The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and open it to install. You may need to allow your file manager to install unknown apps.
4. Plug in a real phone (USB debugging on) to run from Android Studio. SMS, torch, and alarm behavior cannot be tested well on an emulator.
5. In the app, set a PIN (6+ characters), set the authorized controller number in international format, optionally set a backup location number, and grant the permissions shown in the setup checklist.
6. On Android 13+ you may need: Settings > Apps > Theft Guard > ⋮ > Allow restricted settings before granting SMS access.

## Use it
Send an SMS to the phone's number from another phone:

| Text | Does |
|---|---|
| `/find 123456` | Max-volume alarm, torch flash, lost screen, and location SMS to the controller and backup number every minute, up to 30 updates |
| `/find 123456 Call 09xxxxxxx` | Same, with your own message on the screen |
| `/where 123456` | Silent: just texts back the location |
| `/stop 123456` | Stops everything |

Commands from numbers other than the saved controller are silently ignored. Wrong PIN = silently ignored; 5 wrong tries in 10 min = locked out for the rest of the window.

## Known limits (MVP)
- This does NOT block power-off or the home button. That needs Device Owner + Lock Task Mode (phase 2).
- Needs cellular service for the SMS command and replies. A regular Android app cannot turn mobile data on remotely; GPS location is sent by SMS and does not require mobile data.
- Location updates can incur SMS charges, especially when a backup number is configured.
- Android 12+ may block starting the service from the background; the app asks for "draw over apps" and battery exemption to work around it. Test on your phone, and check `adb logcat -s TheftGuard` if it stays silent.
- Dual SIM: reply goes out on the default SMS SIM.
