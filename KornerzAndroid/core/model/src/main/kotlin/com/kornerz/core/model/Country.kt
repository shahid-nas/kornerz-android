package com.kornerz.core.model

import kotlinx.serialization.Serializable

/**
 * One row in the bundled country-code catalog (`country_codes.json`).
 * Flag emoji is derived at load time from `code`; the bundle only
 * carries ISO-3166-1 alpha-2, English name, and dial code.
 */
@Serializable
data class Country(
    val code: String,
    val name: String,
    val dialCode: String,
)

/**
 * Top-level wrapper for the bundled catalog so the JSON shape matches
 * `{ "countries": [...] }` and parsing can be added/extended without
 * breaking the wire format.
 */
@Serializable
data class CountryCodes(
    val countries: List<Country>,
)