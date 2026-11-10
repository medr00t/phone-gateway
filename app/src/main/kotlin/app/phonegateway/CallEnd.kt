package app.phonegateway

object CallEnd {
    const val NETWORK = "network"
    const val APP = "app"
    const val SMS = "sms"

    fun of(stillConnected: Boolean, smsSeen: Boolean): String = when {
        smsSeen -> SMS
        stillConnected -> APP
        else -> NETWORK
    }
}
