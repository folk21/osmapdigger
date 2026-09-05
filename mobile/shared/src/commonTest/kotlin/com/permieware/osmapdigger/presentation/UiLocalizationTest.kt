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
        assertEquals("Избранное", russian.favorites)
        assertEquals("Favorites", english.favorites)
        assertEquals("В избранном: 3", russian.favoriteCount(3))
        assertEquals("Favorites: 3", english.favoriteCount(3))
        assertEquals("Экспорт", russian.exportFavorites)
        assertEquals("Export", english.exportFavorites)
        assertTrue(russian.favoriteExportCompleted.contains("Экспорт"))
        assertTrue(english.favoriteExportFailed.contains("exported"))
        assertEquals("Выбрано: 2", russian.selectedFavorites(2))
        assertEquals("Copy to import list (2)", english.copyFavoritesToImport(2))
        assertTrue(russian.batchExternalSearchHint.contains("нажатию"))
        assertTrue(english.noBatchExternalProviders.contains("batch search"))
        assertEquals("Настройки внешнего поиска", russian.externalSearchSettings)
        assertEquals("External search settings", english.externalSearchSettings)
        assertTrue(russian.datasetSearchTerms("дом недвижимость").contains("дом недвижимость"))
        assertTrue(english.emptyCustomSearchTermsHint.contains("empty"))
        assertEquals("Установить дефолтные", russian.setDefaultSearchTerms)
        assertEquals("Set defaults", english.setDefaultSearchTerms)
        assertEquals("Сохранить настройки поиска", russian.saveSearchSettings)
        assertEquals("Save search settings", english.saveSearchSettings)
        assertTrue(russian.externalSearchTermsHint.contains("Сохранить настройки поиска"))
        assertEquals("Оценка недоступна", russian.scoreUnavailable)
        assertEquals("Score unavailable", english.scoreUnavailable)
        assertEquals("Оценка 82", russian.scoreAndCoverage(82, 100))
        assertEquals("Оценка 82 · полнота 75%", russian.scoreAndCoverage(82, 75))
        assertTrue(russian.favoriteCurrentAnalysis(4, 81, 75, true).contains("рейтинг #4"))
        assertEquals("Не соответствует текущим условиям анализа.", russian.favoriteNotInCurrentResults)
        assertEquals("Does not match the current analysis criteria.", english.favoriteNotInCurrentResults)
        assertEquals("Заметка", russian.favoriteNote)
        assertEquals("Update", english.updateFavoriteSnapshot)
        assertTrue(russian.favoriteSnapshotSummary(81, 75, true).contains("81"))
        assertEquals("рейтинг #4", russian.favoriteCurrentAnalysis(4, null, 0, false))
        assertEquals("рейтинг #4 · оценка недоступна", russian.favoriteCurrentAnalysis(4, null, 0, true))
        assertEquals("Сохранённый анализ", russian.favoriteSnapshotSummary(null, 0, false))
        assertEquals("Координаты: 55.19, 29.75", russian.coordinates("55.19", "29.75"))
        assertEquals(
            "максимум баллов: до 1 км · без баллов: от 10 км",
            russian.bestLowerSummary("1 км", "10 км"),
        )
        assertEquals(
            "maximum points: from 15 km · no points: up to 3 km",
            english.bestHigherSummary("15 km", "3 km"),
        )
        assertEquals("Максимум баллов до", russian.bestLower)
        assertEquals("Без баллов от", russian.zeroHigher)
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
