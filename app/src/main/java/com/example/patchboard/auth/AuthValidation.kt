package com.example.patchboard.auth

/** Pure input checks, kept free of Android types so they are unit-testable. */
object AuthValidation {
    const val MIN_PASSWORD = 6          // Firebase's own minimum
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$")

    fun isValidEmail(email: String) = EMAIL.matches(email.trim())

    fun isValidPassword(password: String) = password.length >= MIN_PASSWORD

    /** Trims and collapses inner whitespace: "  Rere   S. " -> "Rere S.". */
    fun cleanName(name: String) = name.trim().replace(Regex("\\s+"), " ")

    fun isValidName(name: String) = cleanName(name).length in 2..40
}
