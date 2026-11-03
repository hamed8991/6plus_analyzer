# 6+ Poker Analyzer — capture prototype

This is an early Android screen-capture prototype, not a finished poker analyzer.

- Android 8.0+ (API 26+)
- Requests screen-capture permission through Android MediaProjection
- Runs a foreground service and saves a throttled screenshot locally in the app-specific external files directory (`latest-screen.png`)
- Does not upload screenshots

Not implemented yet: card/odds recognition, hand ranking, player/dealer/tie probability calculations, and expected-value analysis. Those need real-screen testing before they can be trusted.

GitHub Actions builds a debug APK on pushes to `main`; download the artifact from the workflow run.
