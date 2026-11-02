package app.phonegateway

object CallVerdict {
    fun decide(pickedUp: Boolean, smsReply: Boolean): String =
        if (pickedUp) "VOICEMAIL" else if (smsReply) "ALIVE" else "OFFLINE"
}
