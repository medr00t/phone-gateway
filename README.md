# Phone Gateway

Android app that turns a spare phone into an SMS + call gateway with a small HTTP API.

## Build
`./gradlew :app:assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk` (install with `adb install -r`).

## Phone setup (once)
1. Install the APK, open it, press **1** (permissions), **3** (battery optimisation off), **2** (start).
2. Settings > Apps > Phone Gateway > Battery: "Unrestricted" (some makers need "autostart" too).
3. Keep the phone plugged in. Note the token shown on screen.

## JSON of many messages (different texts, different receivers)
See `examples/messages.json`. Accepted: a list `[ {to,text}, ... ]` or `{ "delayMs": 3000, "messages": [ ... ] }`.
`to` may be a list of numbers (same text to each). Optional `ref` is echoed back.
Import it from the app (button "Importer un fichier JSON") or via the API.

## API (header `Authorization: Bearer <token>`)
- `GET  /api/status`
- `POST /api/sms` `{"to","text","ref"}`
- `POST /api/sms/batch` the JSON above -> `{batchId, accepted, errors[]}`
- `POST /api/call` `{"to","hangupAfterSec"}` -> job; result has `durationSec` and `connected` (>0 = picked up)
- `GET  /api/jobs?batch=&status=&limit=`, `GET /api/jobs/{id}`
- `GET  /api/inbox?since=<ms>&from=<number>` received SMS (or set a webhook in the app)

## Webhooks (phone -> your server)
Set once with `POST /api/settings {"jobWebhookUrl":"https://…","webhookUrl":"https://…"}` (or in the app).
Every POST carries header `X-Gateway-Token: <token>`; 3 tries (2 s, 10 s, 30 s) until a 2xx answer.
- `jobWebhookUrl`: one event per final status of each job: `{"event":"job.status","id","kind","to","ref","status":"SENT|DELIVERED|FAILED|DONE","error",…}`
- `webhookUrl`: each received SMS: `{"event":"sms.received","from","text","at"}`

## Calling a tracker's SIM
`POST /api/call {"to","hangupAfterSec":25,"waitSmsSec":30,"probeText":""}`. The job ends with a `verdict`:
- `VOICEMAIL`: the line was picked up (a tracker never answers: operator voicemail or a person) -> contact the client
- `ALIVE`: rejected, and the tracker texted back within `waitSmsSec` -> on, with network (garage / basement)
- `OFFLINE`: rejected and no SMS -> SIM without credit, or box off / unplugged
`probeText` (optional, e.g. `STATUS#`) is texted to the SIM if it did not answer by itself, then the wait restarts.
Not every tracker texts back after a call (GT06/TK103 usually do; a Teltonika needs its call/SMS settings checked, or a probe command).

## SMSGate-compatible API (drop-in for clients written for SMSGate's local server)
`POST /message` and `GET /message/{id}`, Basic auth: any user name (the app shows `sms`), password = the token.
Request: `{"id","textMessage":{"text"},"phoneNumbers":[…],"withDeliveryReport"}`; answer: `{"id","state":"Pending|Processed|Sent|Delivered|Failed","recipients":[{"phoneNumber","state","error"}]}`.
A repeated `id` answers `409`, an unknown one `404`. `simNumber` and `ttl` are accepted and ignored.
Client setup (e.g. the hospital back-end, per site): IP of the phone, port `8088`, user `sms`, password = token. No code change.

## Releases
`./release.sh 1.2.0` tags `v1.2.0` and pushes it; GitHub Actions runs the tests, builds the APK signed with the permanent key
and publishes it as a GitHub release (`PhoneGateway-v1.2.0.apk` + SHA-256). Version name comes from the tag, version code from the run number.
Needed once, in the repo's Settings > Secrets and variables > Actions: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Keep a backup of the keystore: with another key, phones must uninstall the app before installing an update.
