# Photostat Master (Master Sahib PDF Viewer)

An Android PDF viewer, document scanner, and photostat toolkit built for Pakistan (`com.themastersahib.pdfviewer`):

1. **Save any page as a proper image** -- one tap exports the current page as a
   high-resolution JPEG straight to your Gallery (sharper than a screenshot,
   rendered at 2500 px wide).
2. **CamScanner-style document scanning & Photostat tools** -- capture documents
   with the camera, auto edge-detection + perspective correction, apply bulk or
   per-page filters, reorder/rotate/delete pages, export to PDF (A4/Letter/Legal,
   200/300 DPI), Collage, or **CNIC Duplex sheet** (2x5 flip-aligned).
3. **Bilingual OCR (English + Urdu/Nastaliq)** -- on-device ML Kit for Latin script
   plus bundled Tesseract (`urd.traineddata` + `eng.traineddata`) for Urdu/Nastaliq.

Built with **Kotlin + Jetpack Compose (Material 3)**.

---

## What Works Now

| Feature | File | Status |
|---|---|---|
| Open PDF from storage, "Open with", & Share (`ACTION_VIEW` + `ACTION_SEND`) | `AndroidManifest.xml`, `MainActivity.kt` | Ready |
| Password-protected PDF unlock prompt | `ui/ReaderScreen.kt`, `core/PdfTextSearch.kt` | Ready |
| Render + scroll pages (vertical scroll / horizontal swipe toggle) | `ui/ReaderScreen.kt`, `PdfRendererHelper.kt` | Ready |
| **Save page as image** (hi-res 2500 px) | `ImageSaver.kt` | Ready |
| Document scanner (**auto-crop** built-in) | `MainActivity.kt` (ML Kit) | Ready |
| Scan review: **bulk + per-page filters** (Grayscale / B&W Print / Magic) | `ui/ScanExportScreen.kt`, `core/ImageFilters.kt` | Ready |
| Scan page editing: **add more pages, reorder, rotate, delete** | `ui/ScanExportScreen.kt` | Ready |
| Scan -> **PDF** (selectable A4/Letter/Legal & 200/300 DPI) | `core/PdfBuilder.kt`, `ui/ScanExportScreen.kt` | Ready |
| **Collage** mode | `core/CollageLayout.kt` | Ready |
| **CNIC duplex mode** (2x5 symmetric flip-aligned) + direct print | `core/CnicLayout.kt` | Ready |
| **Annotation** (pen / highlighter / signature) -> image, flattened PDF, or vector PDF (text kept) | `ui/AnnotationScreen.kt`, `core/PdfTools.kt` | Ready |
| **Home dashboard**: Recent + Bookmarks, filter search bar, resume last-read page, dark mode | `ui/HomeScreen.kt`, `data/CloudStore.kt` | Ready |
| **Authentication**: Email/Password (with password reset) + **Continue with Google** | `ui/LoginScreen.kt`, `data/AuthRepository.kt` | Ready |
| **Reader**: pinch/double-tap zoom, night mode, page slider, thumbnails, **text search with in-page word highlighting** | `ui/ReaderScreen.kt`, `core/PdfTextSearch.kt` | Ready |
| **OCR** scanned pages -> copy + .txt (ML Kit Latin + Tesseract Urdu/Nastaliq bundled) | `core/Ocr.kt`, `core/UrduOcr.kt`, `assets/tessdata/` | Ready |
| **Share + Print** PDF from reader | `core/SharePrint.kt` | Ready |
| **Merge / Split / Rotate** PDFs | `core/PdfTools.kt` | Ready |
| **In-app Auto-Update** from GitHub Releases (`latest-version.json`) | `core/UpdateChecker.kt`, `MainActivity.kt` | Ready |
| **Unit Test Suite** (CNIC duplex symmetry, paper conversion, B&W thresholding) | `app/src/test/.../CoreUnitTest.kt` | Ready |

---

## CNIC Mode -- Printing Steps
1. Scan CNIC **front** (page 1) and **back** (page 2).
2. Tap **CNIC Mode** -> sheet saves to Gallery and opens the system print dialog.
3. Print the page, put the **same sheet** back in the tray, and print the **same page** again, flipping on the **LONG edge** (left-to-right). Every card's back aligns directly behind its front.

---

**The Master Sahib** - www.mastersahib.com
