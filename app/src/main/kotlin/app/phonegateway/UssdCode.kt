package app.phonegateway

object UssdCode {
    private val PATTERN = Regex("^[*#][0-9*#]{1,60}#$")

    fun isValid(code: String) = PATTERN.matches(code)
}
