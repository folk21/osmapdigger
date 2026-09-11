package com.permieware.osmapdigger.presentation

import com.permieware.osmapdigger.domain.MetricDefinition

/** Presentation-only localized labels for current stable metric categories and measure semantics. */
object MetricDisplayNameResolver {
    fun resolveCompact(definition: MetricDefinition, language: UiLanguage): String =
        if (language == UiLanguage.RUSSIAN) {
            RussianCategoryNames[definition.categoryId] ?: definition.title
        } else {
            definition.title
        }

    fun resolve(definition: MetricDefinition, language: UiLanguage): String {
        if (language == UiLanguage.ENGLISH) return definition.title
        val category = RussianCategoryNames[definition.categoryId] ?: return definition.title
        return when (definition.measureType) {
            "distance" -> "$category — расстояние"
            "count" -> countLabel(category, definition.id)
            "coverage" -> coverageLabel(category, definition.id)
            else -> category
        }
    }

    private fun countLabel(category: String, metricId: String): String =
        extractRadiusKm(metricId, "count_")?.let { "$category — количество в радиусе $it км" }
            ?: "$category — количество"

    private fun coverageLabel(category: String, metricId: String): String =
        extractRadiusKm(metricId, "coverage_pct_")?.let { "$category — покрытие в радиусе $it км" }
            ?: "$category — покрытие"

    private fun extractRadiusKm(metricId: String, marker: String): String? {
        val suffix = metricId.substringAfter(marker, missingDelimiterValue = "")
        if (suffix.isBlank() || !suffix.endsWith("km")) return null
        return suffix.removeSuffix("km").replace('_', '.').takeIf { it.toDoubleOrNull() != null }
    }
}

private val RussianCategoryNames =
    mapOf(
        "forest" to "Лес",
        "river_stream" to "Река или ручей",
        "wetland" to "Болото",
        "spring" to "Источник",
        "beach" to "Пляж",
        "farmland" to "Сельхозугодья",
        "meadow" to "Луг",
        "orchard" to "Сад",
        "farmyard" to "Фермерская территория",
        "industrial" to "Промышленная зона",
        "landfill" to "Свалка",
        "quarry" to "Карьер",
        "brownfield" to "Заброшенная промзона",
        "wastewater" to "Очистные сооружения",
        "cemetery" to "Кладбище",
        "military" to "Военная территория",
        "power_plant" to "Электростанция",
        "power_substation" to "Электроподстанция",
        "power_line" to "Линия электропередачи",
        "railway_station" to "Ж/д станция",
        "railway_line" to "Железная дорога",
        "bus_stop" to "Автобусная остановка",
        "bus_station" to "Автовокзал",
        "major_road" to "Крупная дорога",
        "regional_road" to "Региональная дорога",
        "local_road" to "Местная дорога",
        "track_road" to "Грунтовая дорога",
        "school" to "Школа",
        "kindergarten" to "Детский сад",
        "medical" to "Медицинское учреждение",
        "pharmacy" to "Аптека",
        "supermarket" to "Супермаркет",
        "convenience_shop" to "Магазин",
        "fuel" to "АЗС",
        "fire_station" to "Пожарная часть",
        "police" to "Полиция",
        "post_office" to "Почта",
        "protected_area" to "Охраняемая территория",
        "park" to "Парк",
        "tourism" to "Туристический объект",
    )
