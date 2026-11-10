package app.phonegateway

object SimChoice {
    fun resolve(requested: Int?, configured: Int): Int? = requested ?: configured.takeIf { it > 0 }

    fun <T> withLegacy(hasValue: Boolean, value: T, legacy: T): T = if (hasValue) value else legacy
}
