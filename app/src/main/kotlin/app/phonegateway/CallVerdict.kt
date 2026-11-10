package app.phonegateway

object CallVerdict {
    const val MIN_NETWORK_RING_SEC = 6

    fun releasedByNetwork(endedBy: String?, ringSec: Int): Boolean = endedBy == CallEnd.NETWORK && ringSec >= MIN_NETWORK_RING_SEC

    fun decide(pickedUp: Boolean, smsReply: Boolean, releasedByNetwork: Boolean = false): String =
        if (smsReply) "ALIVE" else if (pickedUp || releasedByNetwork) "VOICEMAIL" else "OFFLINE"
}
