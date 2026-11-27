package com.pearparinya.automovie

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CastOutfitTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun posterAndSceneResolveSameApprovedFile() {
        val project = folder.newFolder()
        val poster = CastOutfit("PIA", "white shirt, black buttons")
        assertNull(poster.attachment(project))
        val file = poster.file(project)
        file.parentFile!!.mkdirs()
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(poster.attachment(project), CastOutfit("pia", "white shirt, black buttons").attachment(project))
        assertNull(CastOutfit("PIA", "different shirt").attachment(project))
        assertNull(CastOutfit("OTHER", poster.outfit).attachment(project))
        assertNull(poster.attachment(folder.newFolder()))
    }
}
