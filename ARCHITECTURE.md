# Photostat Master (Master Sahib PDF Viewer) -- Architecture

```
PDF_Viewer_App
│
├── 1. App Configuration & Manifest Layer
│   ├── AndroidManifest.xml      Ready  PDF "Open with" chooser + Share intent (ACTION_VIEW + ACTION_SEND), scanner, install packages
│   └── build.gradle.kts         Ready  Compose, ML Kit scanner, Tesseract Urdu OCR, Firebase Auth/Firestore, ARM ABI filter
│
├── 2. UI / Presentation Layer
│   ├── Splash & Auth Gate       Ready  Splash (core-splashscreen) + LoginScreen (Email/Password + Google Sign-In + Password Reset)
│   ├── Home / Dashboard         Ready  ui/HomeScreen.kt (Recent + Bookmarks tabs, filter search, dark mode toggle, logout)
│   │   ├── Recent Files         Ready  CloudStore (Firestore per-uid with offline persistence), last page resume
│   │   ├── Favorites/Bookmarks  Ready  CloudStore (Firestore per-uid, add + delete bookmarks)
│   │   └── Local File Browser   Ready  SAF system picker + Browse Device & Merge PDFs shortcuts
│   ├── PDF Reader Screen        Ready  ui/ReaderScreen.kt — Pdfium view, zoom, password unlock, "Save page as image"
│   │   ├── Top Action Bar       Ready  search/night/thumbs/bookmark/annotate/save-image + scroll toggle/share/print/rotate/split
│   │   ├── PDF Canvas/Page View Ready  AndroidPdfViewer (pinch + double-tap zoom + in-page search word highlight overlay)
│   │   └── Bottom Tool Bar      Ready  page slider + annotation (pen/highlight/signature)
│   └── Document Scanner Screen  Ready  ML Kit (auto edge-detect + crop) + ScanExportScreen
│       ├── Camera Preview       Ready  ML Kit UI
│       └── Crop & Filter View   Ready  Bulk + per-page filters, add/reorder/rotate/delete pages, paper size/DPI selector
│
├── 3. Core Engines Layer
│   ├── A. PDF Rendering & Manipulation Engine
│   │   ├── Page Decoder         Ready  Pdfium (android-pdf-viewer) for view; PdfRenderer for image/thumb export
│   │   ├── Text Extractor       Ready  PdfBox-Android -> text search + glyph bounding-box word highlighting
│   │   └── Annotation & Tools   Ready  Flatten save + vector-into-original-PDF (text kept), merge/split/rotate
│   ├── B. Scanner & OCR Engine
│   │   ├── Edge & Perspective   Ready  ML Kit Document Scanner
│   │   └── OCR Engine           Ready  ML Kit (Latin, core/Ocr.kt) + Tesseract (Urdu/Nastaliq, core/UrduOcr.kt + assets/tessdata)
│   └── C. Self-Update Engine
│       └── UpdateChecker        Ready  Checks latest-version.json on GitHub Releases, downloads APK with redirect handling, installs
│
└── 4. Data & Storage Layer
    ├── CloudStore (Firestore)   Ready  data/CloudStore.kt — per-user recents, last page, bookmarks (offline persistence enabled)
    ├── AuthRepository           Ready  data/AuthRepository.kt — Firebase Auth + Google Sign-In
    ├── SharedPreferences        Ready  data/SettingsStore.kt — dark mode, vertical/horizontal scroll direction
    └── File System Manager      Ready  ImageSaver + FileExport; PdfTools; SharePrint
```

---
**The Master Sahib** - www.mastersahib.com
