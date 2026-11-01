package app.phonegateway

/**
 * What a call to a tracker's SIM tells us:
 *  - the line was picked up           -> VOICEMAIL (a tracker never answers: operator voicemail, or a person)
 *  - rejected + the tracker texts back -> ALIVE     (on, with network: e.g. parked in a garage / basement)
 *  - rejected + no text                -> OFFLINE   (SIM without credit / box off or unplugged)
 */
object CallVerdict {
    fun decide(pickedUp: Boolean, smsReply: Boolean): String =
        if (pickedUp) "VOICEMAIL" else if (smsReply) "ALIVE" else "OFFLINE"
}
