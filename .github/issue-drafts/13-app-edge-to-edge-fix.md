# [app] Phone A/B selection hidden under the title bar on Android 16

On the Pixel 7 Pro (Android 16) the first row of the main screen (Phone A / B) is drawn under the
title bar and cannot be reached by scrolling. Android 15+ draws apps edge-to-edge (enforced from
Android 16), and the layout did not account for the system bars.

## Tasks
- [ ] Use a theme without the old action bar and show the app name as the first line of the layout
- [ ] Pad the scrolling root by the system-bar, display-cutout and keyboard insets (`ViewCompat.setOnApplyWindowInsetsListener`)
- [ ] `windowSoftInputMode="adjustResize"` so the fields stay reachable while the keyboard is open

## Done when
- On both Pixels the Phone A/B row is visible and every field can be reached, with and without the keyboard.
