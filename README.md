# Master Sahib PDF Viewer

An Android PDF viewer with two headline features:

1. **Save any page as a proper image** — one tap exports the current page as a
   high-resolution JPEG straight to your Gallery (sharper than a screenshot,
   because it renders the PDF at ~2500 px wide instead of your screen size).
2. **CamScanner-style document scanning** — capture documents with the camera,
   auto edge-detection + perspective correction, and save as PDF or images.

Built with **Kotlin + Jetpack Compose**. Free, no paid SDKs.

---

## How to open & build

1. Open **Android Studio** (Koala / 2024.1 or newer).
2. `File ▸ Open` → select this folder (`Master Sahib PDF Viewer`).
3. Let Gradle sync (first sync downloads dependencies — needs internet).
4. Plug in an Android phone (USB debugging on) or start an emulator
   with **Google Play** image (ML Kit scanner needs Play Services).
5. Press **Run ▶**.

> Note: Android Studio will generate the Gradle **wrapper** (`gradlew`,
> `gradle/wrapper/`) on first open, and the launcher icon PNGs can be made via
> `File ▸ New ▸ Image Asset`. The `.kt` source and config here are the real code.

---

## What works now

| Feature | File | Status |
|---|---|---|
| Open PDF from storage | `MainActivity.kt` | ✅ |
| **Open PDF from "Open with"** (tap a PDF in any app) | `AndroidManifest.xml` | ✅ |
| Render + scroll pages | `PdfRendererHelper.kt` | ✅ |
| **Save page as image** (hi-res) | `ImageSaver.kt` | ✅ |
| Document scanner (**auto-crop** built-in) | `MainActivity.kt` (ML Kit) | ✅ |
| Scan review + **bulk filters** (Grayscale / B&W Print / Magic) | `ui/ScanExportScreen.kt`, `core/ImageFilters.kt` | ✅ |
| Scan page editing: **add more pages, rotate, delete** | `ui/ScanExportScreen.kt` | ✅ |
| Scan → **PDF** | `core/PdfBuilder.kt` | ✅ |
| **Collage** mode | `core/CollageLayout.kt` | ✅ |
| **CNIC duplex mode** (2×5, flip-aligned) + direct print | `core/CnicLayout.kt` | ✅ |
| **Annotation** (pen / highlighter / signature) → flatten → save image/PDF | `ui/AnnotationScreen.kt` | ✅ |
| **Home dashboard**: Recent + Bookmarks, **last-read page** resume, **dark mode** | `ui/HomeScreen.kt`, `data/AppDatabase.kt` | ✅ |
| **Reader**: pinch/double-tap **zoom**, **night mode**, **page slider**, **thumbnails**, **text search** | `ui/ReaderScreen.kt`, `core/PdfTextSearch.kt` | ✅ |
| **OCR** scanned pages → copy + .txt (ML Kit, on-device) | `core/Ocr.kt` | ✅ |
| **Urdu/nastaliq OCR** (Tesseract — needs traineddata, see assets/tessdata) | `core/UrduOcr.kt` | ✅ |
| **Share + Print** PDF from reader | `core/SharePrint.kt` | ✅ |
| **Merge / Split / Rotate** PDFs | `core/PdfTools.kt` | ✅ |
| **Vector annotation** into original PDF (text kept) | `core/PdfTools.kt`, `ui/AnnotationScreen.kt` | ✅ |
| **Splash screen** + app icon | `res/`, `App`/`MainActivity` | ✅ |

> Full layered design → see **ARCHITECTURE.md**.

### CNIC mode — printing steps
1. Scan CNIC **front** (page 1) and **back** (page 2).
2. Tap **CNIC Mode** → sheet Gallery mein save + print dialog khulega.
3. Page print karein, phir **wahi sheet dobara** tray mein daal ke **wahi page** print karein, **LONG edge se flip** (left↔right). Back har card ke front ke peechhe aa jayegi.

## Auto-build APK (GitHub Actions)

A workflow at `.github/workflows/build-apk.yml` builds a **fresh debug APK on every push**.
Push this project to any GitHub repo → open the **Actions** tab → latest run →
download the **master-sahib-pdf-viewer-debug** artifact (the APK). No local SDK needed.

## Roadmap

- **Phase 3 — Annotation:** highlight, freehand draw, signature (Compose Canvas overlay).
- **Phase 4 — Editing:** merge / split / rotate / delete pages (PdfBox-Android, Apache 2.0 — dependency already commented in `app/build.gradle.kts`).
- **Phase 5 — Convert / Export + OCR:** PDF → image batch, compress, text extract (ML Kit Text Recognition).
- Pinch-to-zoom, thumbnails grid, recent files, dark mode.

---

## Key design notes

- **PdfRenderer** (`android.graphics.pdf`) is built into Android — no license,
  no cost. It renders a page into a `Bitmap` at any resolution: that single
  capability powers both the on-screen view and the image export.
- **Saving to Gallery** uses `MediaStore` with scoped storage, so on Android 10+
  the app needs **no storage permission**. Images land in
  `Pictures/Master Sahib PDF`.
- **ML Kit Document Scanner** (`play-services-mlkit-document-scanner`) provides a
  full, polished scanning UI out of the box and returns both JPEG pages and a
  ready PDF — this is the "CamScanner" job, done on-device and free.

---

**The Master Sahib** · themastersahib.com
