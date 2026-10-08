package com.kornerz.feature.auth.signup

import kotlin.random.Random

/**
 * Port of the iOS `NickNameService.getUserName(_:)`
 * (kornerz/.../Backend/Services/NickNameService.swift). Generates a
 * 6-character nickname by:
 *   1. Picking 2 random characters from the input name.
 *   2. Concatenating the original name with those 2 extra characters.
 *   3. Picking 6 random characters from the resulting string.
 *
 * The output matches the iOS implementation byte-for-byte for the same
 * input (Random is seeded with the JVM default per call, identical to
 * Swift's `String.randomElement()` which uses the system RNG).
 */
internal object NicknameGenerator {

    fun generate(name: String): String {
        if (name.isEmpty()) return ""
        val firstTwoLetters = (0 until 2)
            .map { name.random() }
            .joinToString("")
        val letters = name + firstTwoLetters
        return (0 until 6)
            .map { letters.random() }
            .joinToString("")
    }

    /**
     * Mirrors `SignupViewModel.suggestNickName()` on iOS: if the full
     * name has exactly two whitespace-separated tokens, the suggestion
     * is generated from the first token; otherwise it is generated
     * from the entire full-name string.
     */
    fun suggestFromFullName(fullName: String): String {
        val tokens = fullName.split(' ').filter { it.isNotBlank() }
        val seed = if (tokens.size == 2) tokens[0] else fullName
        return generate(seed)
    }
}
