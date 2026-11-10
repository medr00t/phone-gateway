package app.phonegateway

object CallVerdict {
    fun decide(pickedUp: Boolean, smsReply: Boolean): String =
        if (smsReply) "ALIVE" else if (pickedUp) "VOICEMAIL" else "OFFLINE"
}
