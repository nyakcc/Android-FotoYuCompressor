# FotoYu Compressor Android v1.2

Android version of FotoYu Compressor. It scans a selected folder recursively, processes photos only, resizes/compresses previews below 1 MB, and creates output folders with a configurable maximum number of photos (default 2,000).

## Build
Open this folder in Android Studio and let Gradle sync. Then Build > Build APK(s).

## Notes
- Uses Android's Storage Access Framework; user selects the source and destination folders.
- Videos such as MOV/MP4 are ignored entirely.
- The output is JPEG preview; originals remain untouched.
- Default preview width is 1280 px.
