package com.themastersahib.pdfviewer

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Required once before using PdfBox-Android (text extraction for search).
        PDFBoxResourceLoader.init(this)
    }
}
