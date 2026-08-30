package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.error.OperationalFailureKind
import com.permieware.osmapdigger.ui.filterSummaryText
import com.permieware.osmapdigger.ui.metricFilterText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UiLocalizationTest {
    @Test
    fun russianIsTheDefaultUiLanguage() {
        assertEquals(UiLanguage.RUSSIAN, UiLocalization.defaultLanguage)
        assertEquals("Русский", UiLocalization.strings(UiLocalization.defaultLanguage).language.displayName)
    }

    @Test
    fun russianAndEnglishCatalogsExposeIndependentChromeText() {
        val russian = UiLocalization.strings(UiLanguage.RUSSIAN)
        val english = UiLocalization.strings(UiLanguage.ENGLISH)

        assertEquals("Поиск", russian.searchTab)
        assertEquals("Search", english.searchTab)
        assertEquals("Выбрать центр на карте", russian.pickCenterOnMap)
        assertEquals("Pick center on map", english.pickCenterOnMap)
        assertEquals("Источник кандидатов", russian.candidateSource)
        assertEquals("Candidate source", english.candidateSource)
        assertEquals("Отключить импорт", russian.deactivateImportedCandidates)
        assertEquals("Disable import", english.deactivateImportedCandidates)
        assertEquals("Выбрать всех", russian.selectAll)
        assertEquals("Select all", english.selectAll)
        assertEquals("В список", russian.shortlist)
        assertEquals("Shortlist", english.shortlist)
        assertEquals("В списке: 3", russian.shortlistCount(3))
        assertEquals("Shortlisted: 3", english.shortlistCount(3))
        assertNotEquals(
            russian.operationalFailureMessage(OperationalFailureKind.FILE_ACCESS),
            english.operationalFailureMessage(OperationalFailureKind.FILE_ACCESS),
        )
        assertNotEquals(russian.details, english.details)
    }

    @Test
    fun localizedFilterAndSummaryAdaptersRemainGeneric() {
        val russian = UiLocalization.strings(UiLanguage.RUSSIAN)
        val filterText = russian.metricFilterText()
        val summaryText = russian.filterSummaryText()

        assertEquals("Мин. расстояние", filterText.minDistance)
        assertTrue(filterText.nearestMappedFeature(" · км").startsWith("Расстояние"))
        assertEquals("не менее 5", summaryText.atLeast("5"))
    }
}
