package com.pearparinya.automovie.stand

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts

class StandMasterAndroidImport(
    private val resolver: ContentResolver,
    private val store: StandMasterStore
) {
    companion object {
        fun forContext(context: android.content.Context): StandMasterAndroidImport =
            StandMasterAndroidImport(context.contentResolver,
                StandMasterStore(context.filesDir, VerifiedStandMasters.records))

        fun documentContract() = ActivityResultContracts.OpenDocument()
        fun imageMimeTypes() = arrayOf("image/*")
    }

    // Call on an IO worker, not the UI thread.
    fun importSelected(standId: String, uri: Uri): Boolean {
        require(StandCatalog.find(standId) != null)
        require(uri.scheme == "content" || uri.scheme == "file")
        return resolver.openInputStream(uri)?.use { store.importOriginal(standId, it) } ?: false
    }

    fun availableIds(): List<String> =
        StandCatalog.all.map { it.id }.filter { store.verifiedFile(it) != null }
}
