# [app] Android project, main screen and permissions

Create the Kotlin Android Studio project and the main screen (proposal §2).

## Tasks
- [ ] Gradle project (Kotlin, minSdk 29, targetSdk 36), package e.g. `lk.uom.cs4473.netmeasure`
- [ ] Manifest permissions: ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, ACCESS_WIFI_STATE, ACCESS_NETWORK_STATE, READ_PHONE_STATE, INTERNET, FOREGROUND_SERVICE, FOREGROUND_SERVICE_LOCATION, POST_NOTIFICATIONS, WAKE_LOCK
- [ ] `MainActivity`: Start / Stop, waypoint buttons P1–P5, inputs for phone ID (A/B), walk ID, time slot (OFFPEAK/PEAK), server URL
- [ ] Runtime permission flow (location must be granted before the service starts)
- [ ] Live view of the last logged row and row count

## Done when
- App builds (`./gradlew assembleDebug`), installs on Pixel 7 Pro, asks for permissions, buttons visible.
