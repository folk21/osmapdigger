package com.permieware.osmapdigger.presentation

/** Presentation-only localization for OSM `place=*` values used by settlement datasets. */
object SettlementPlaceTypeResolver {
    fun resolve(placeType: String?, language: UiLanguage): String? {
        if (placeType.isNullOrBlank() || language == UiLanguage.ENGLISH) return placeType
        return RussianPlaceTypeNames[placeType] ?: placeType
    }
}

private val RussianPlaceTypeNames =
    mapOf(
        "city" to "город",
        "town" to "город",
        "village" to "деревня",
        "hamlet" to "деревня",
        "isolated_dwelling" to "отдельное поселение",
    )
