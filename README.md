# Phone Gateway

Turns a spare Android phone into an SMS and call gateway with a small HTTP API.
Clients written for SMSGate's local server work without changes (see below).

## Setup

1. Install the APK from the latest release and open it.
2. Grant the permissions, turn battery optimisation off, switch the server on.
3. Note the token shown in the app and keep the phone plugged in.

The server listens on port 8088.

## API

Every `/api/*` route needs `Authorization: Bearer <token>`.

| Route | Purpose |
|---|---|
| `GET /api/status` | state and queue size |
| `POST /api/sms` | send one SMS: `{"to","text","ref","sim"}`, `sim` is the slot to send from on a dual-SIM phone |
| `POST /api/sms/batch` | send many SMS |
| `POST /api/call` | place a call: `{"to","hangupAfterSec"}` |
| `POST /api/ussd` | run a USSD code: `{"code":"*123#","ref","sim"}`, `sim` is the slot (1 or 2) on a dual-SIM phone, the reply comes in the job webhook as `response` |
| `GET /api/info` | version, the roles this phone has (SMS, USSD, calls) each with its SIM, and the SIMs |
| `GET /api/sims` | the active SIMs: `slot`, `name`, `carrier`, `number` |
| `GET /api/jobs`, `GET /api/jobs/{id}` | status of messages and calls, filters `batch`, `status`, `limit` |
| `GET /api/inbox?since=&from=` | received SMS |
| `GET /api/settings`, `POST /api/settings` | webhook URLs |

### Batch

Send a list of `{to, text, ref?, id?}`, or `{"delayMs": 3000, "messages": [...]}`.
`to` can be a list: the same text goes to each number. An `id` must be unique and is refused for that message if already used.
Limits: 1000 messages per request, 1600 characters per text, 2 s between messages unless `delayMs` is set.
The reply holds `batchId`, `accepted`, `messages` and `errors`. See `examples/messages.json`.

### Calls

`POST /api/call {"to","hangupAfterSec":25,"waitSmsSec":30,"probeText":""}` ends with a `verdict`:

- `VOICEMAIL`: the call was picked up
- `ALIVE`: the call was rejected and an SMS came back from that number within `waitSmsSec`
- `OFFLINE`: the call was rejected and nothing came back

`probeText` is sent by SMS when nothing came back, then the wait starts again.

## SMSGate-compatible API

`POST /message` and `GET /message/{id}`, Basic auth: user `sms`, password = token.

```json
{"id": "abc", "textMessage": {"text": "Hello"}, "phoneNumbers": ["+212600000000"]}
```

The reply holds `state` (`Pending`, `Processed`, `Sent`, `Delivered`, `Failed`) and one state per recipient.
A repeated `id` answers `409`, an unknown one `404`. Point the client at the phone's IP, port 8088, and it works.

## Dual-SIM

In the app's settings, switch on what this phone does (send SMS, run USSD codes, place calls) and pick a SIM for each: SMS, USSD and calls can use three different SIMs. "Default" keeps the phone's own choice. A server reads the roles from `GET /api/info`. A request can still override the SIM with `"sim": 1` or `2`; `GET /api/sims` lists the SIMs.

## Webhooks

```
POST /api/settings {"jobWebhookUrl": "https://...", "webhookUrl": "https://..."}
```

- `jobWebhookUrl` gets one `job.status` event per final status (`SENT`, `DELIVERED`, `FAILED`, `DONE`):
  `{"event","id","kind","to","ref","status","error"}`
- `webhookUrl` gets each received SMS: `{"event":"sms.received","from","text","at"}`

Every request carries `X-Gateway-Token`. Anything but a 2xx is retried after 2 s, 10 s and 30 s, then dropped.

## Build

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Releases

Merging into `main` publishes a release when `version.txt` holds a version that has none yet.
GitHub Actions runs the tests, signs the APK and attaches it with its SHA-256. `./release.sh X.Y.Z` does the same from a tag.

The repo needs four secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Back up the keystore: with another key, phones must uninstall the app before updating.

## Retention

Messages, calls and received SMS are deleted after 30 days. The check runs when the app opens and every 6 hours.
