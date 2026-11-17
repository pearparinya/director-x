package com.pearparinya.automovie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverTypographyPolicyTest {
    @Test fun socialPosterHasVerticalSafeArea() {
        val text = CoverTypography.safeZone("9:16")
        assertTrue(text.contains("bottom 25%"))
        assertTrue(text.contains("right 20%"))
        assertTrue(text.contains("Do not cover any face"))
    }

    @Test fun genreTypographyChangesWithStoryCategory() {
        val horror = CoverTypography.genreStyle("สยองขวัญและวิญญาณอาฆาต")
        val drama = CoverTypography.genreStyle("ละครสั้นสะท้อนสังคม")
        assertNotEquals(horror, drama)
        assertTrue(horror.contains("HORROR"))
        assertTrue(drama.contains("LIFE DRAMA"))
    }

    @Test fun landscapeDoesNotInheritVerticalSafeArea() {
        assertFalse(CoverTypography.safeZone("16:9").contains("bottom 25%"))
    }
}
