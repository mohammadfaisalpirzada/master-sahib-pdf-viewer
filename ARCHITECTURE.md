# Master Sahib PDF Viewer — Architecture

Yeh aapke diye hue layered structure ke mutabiq hai. ✅ = ab ban chuka, 🔜 = agle phase.

```
PDF_Viewer_App
│
├── 1. App Configuration & Manifest Layer
│   ├── AndroidManifest.xml      ✅  PDF "Open with" chooser + Share intent, scanner
│   └── build.gradle.kts         ✅  Compose, ML Kit scanner, print
│
├── 2. UI / Presentation Layer
│   ├── Splash & Onboarding      ◐  splash ✅ (core-splashscreen); onboarding 🔜
│   ├── Home / Dashboard         ✅  ui/HomeScreen.kt (tabs + dark mode toggle)
│   │   ├── Recent Files         ✅  Room DB, last page yaad
│   │   ├── Favorites/Bookmarks  ✅  Room DB
│   │   └── Local File Browser   ◐  SAF system picker (Android 13+ permission-safe)
│   ├── PDF Reader Screen        ✅  ui/ReaderScreen.kt — Pdfium view, zoom, "Save page as image"
│   │   ├── Top Action Bar       ✅  open/scan/search/night/thumbs/share/print + rotate/split
│   │   ├── PDF Canvas/Page View ✅  AndroidPdfViewer (pinch + double-tap zoom)
│   │   └── Bottom Tool Bar      ✅  page slider + annotation (pen/highlight/sign); night mode ✅
│   └── Document Scanner Screen  ✅  ML Kit (auto edge-detect + crop + filter built-in)
│       ├── Camera Preview       ✅  ML Kit UI
│       └── Crop & Filter View   ✅  ML Kit crop + filters + add/rotate/delete pages (ScanExportScreen)
│
├── 3. Business Logic / ViewModel Layer
│   ├── FileListViewModel        🔜  (MediaStore query + sort)
│   ├── PdfReaderController      ◐  MainActivity mein inline; alag ViewModel 🔜
│   └── ScannerController        ✅  MainActivity scan flow + ScanExportScreen
│
├── 4. Core Engines Layer
│   ├── A. PDF Rendering Engine
│   │   ├── Page Decoder         ✅  Pdfium (AndroidPdfViewer) for view; PdfRenderer for image/thumb export
│   │   ├── Text Extractor       ✅  PdfBox-Android → text search (find+jump); word-highlight 🔜
│   │   └── Annotation Writer    ✅  flatten save + vector-into-original-PDF (text kept, PdfBox)
│   └── B. Scanner & OCR Engine
│       ├── Edge Detection       ✅  ML Kit (TFLite andar)
│       ├── Perspective Transform✅  ML Kit
│       └── OCR Engine           ✅  ML Kit (Latin, core/Ocr.kt) + Tesseract (Urdu/nastaliq, core/UrduOcr.kt)
│
└── 5. Data & Storage Layer
    ├── Local DB (Room)          ✅  data/AppDatabase.kt — recent, last page, bookmarks
    ├── SharedPreferences        ◐  data/SettingsStore.kt — dark mode ✅; scroll dir stored, use 🔜
    └── File System Manager      ✅  ImageSaver + FileExport; merge/split/rotate (PdfTools); share/print (SharePrint)
```

## Core modules (banaye gaye)

| File | Kaam |
|---|---|
| `core/PaperSize.kt` | A4/Letter/Legal, mm→px, auto portrait/landscape |
| `core/ImageFilters.kt` | Bulk filters: Grayscale, **B&W Print**, Magic |
| `core/PdfBuilder.kt` | Bitmaps → multi-page PDF (`PdfDocument`) |
| `core/CnicLayout.kt` | **CNIC duplex sheet** (2 col × 5 row, flip-aligned) |
| `core/CollageLayout.kt` | Grid collage |
| `core/FileExport.kt` | PDF → Downloads; scan Uri → Bitmap |
| `PdfRendererHelper.kt` | PDF page → Bitmap |
| `ImageSaver.kt` | Page/image → Gallery (MediaStore) |
| `ui/ScanExportScreen.kt` | Filter + Save Images / PDF / Collage / CNIC / Print |
| `ui/AnnotationScreen.kt` | Pen / highlighter / signature → flatten onto page → save image/PDF |
| `ui/HomeScreen.kt` | Dashboard: Recent + Bookmarks tabs, dark mode toggle |
| `data/AppDatabase.kt` | Room: RecentFile + Bookmark entities/DAOs |
| `data/SettingsStore.kt` | SharedPreferences settings + Uri display-name helper |
| `ui/ReaderScreen.kt` | Pdfium viewer: zoom, night, page slider, thumbnails, text search |
| `core/PdfTextSearch.kt` | PdfBox text extract → matching page indices |
| `App.kt` | Application — PdfBox init |
| `core/Ocr.kt` | ML Kit OCR (Latin): bitmap(s) → text |
| `core/UrduOcr.kt` | Tesseract OCR (Urdu/nastaliq) — needs assets/tessdata |
| `core/PdfTools.kt` | Merge / split / rotate + vector annotation into PDF |
| `core/SharePrint.kt` | Share (FileProvider) + print PDF (PrintManager) |

## PDF engines (abhi kya use ho raha hai)
- **Viewing:** `com.github.barteksc:AndroidPdfViewer` (Pdfium) — zoom, night mode, page slider, swipe.
- **Text search:** `com.tom-roush:pdfbox-android` — per-page text extract → find + jump (word-level highlight abhi nahi; glyph coordinates se baad mein).
- **Image/thumbnail export:** Android built-in `PdfRenderer` (`PdfRendererHelper.kt`) — page → Bitmap.
- **Annotation save:** abhi flatten (draw → nayi image/PDF). Vector-into-original-PDF ke liye PdfBox-Android se Annotation Writer baad mein.

Sab free / open-source, koi paid SDK nahi.

> Note: barteksc AndroidPdfViewer thoda purana hai; agar Android 15 (16 KB page) ya maintenance issue aaye to maintained fork `com.github.mhiew:android-pdf-viewer` pe shift ho sakte hain (API almost same).

## Next phases (tarteeb)
1. **Phase 3 — Annotation:** PdfBox-Android se highlight/draw/sign save.
2. **Phase 4 — Reader upgrade:** Pdfium pe switch → text search, night mode, thumbnails, page slider.
3. **Phase 5 — Data layer:** Room (recent/bookmarks/last page) + SharedPreferences (settings) + file browser.
4. **Phase 6 — OCR:** ML Kit Text Recognition (scan → searchable text).

---
**The Master Sahib** · themastersahib.com
