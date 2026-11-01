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
