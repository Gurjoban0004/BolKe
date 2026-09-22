# BolKe language service

The Android keyboard calls the private service only when `languageServiceUrl` is set at build time:

```text
./gradlew assembleRelease -PlanguageServiceUrl=https://your-service.example
```

## Punglish

```json
POST /v1/punglish
{"text":"ਕੀ ਹਾਲ ਆ ਤੂੰ ਕਿੱਥੇ ਓ", "locale":"pa-IN"}
```

## Response

```json
{"punglish":"ki haal aa, tu kithe o?", "alternatives":[], "confidence":0.94}
```

## Copied-message translation

```json
POST /v1/translate
{"text":"Your parcel will arrive Friday.", "target":"pa-Guru", "style":"natural-family"}
```

```json
{"translation":"ਤੁਹਾਡਾ ਪਾਰਸਲ ਸ਼ੁੱਕਰਵਾਰ ਨੂੰ ਆ ਜਾਵੇਗਾ।", "alternatives":[], "confidence":0.96}
```

## Deployment choice

BolKe does not require Cloudflare, BHASHINI, IndicTrans, or IndicXlit for the
family test. One small HTTPS service can implement the two text endpoints using
the language provider selected after real Punjabi evaluation. Keeping this
provider-neutral contract avoids shipping credentials in the APK and avoids
building multiple gateways before they are needed.

The service authenticates the installed app without shipping provider credentials. It must process only the current request, must not log or retain content, and must not train on audio, messages, or translations. Structured failures use `unsafe_input`, `unsupported_text`, timeout, or service errors. Punglish falls back locally during an outage; copied-message translation shows a retryable error instead of inventing a result. Read-aloud uses Android's installed Punjabi voice and never calls a paid speech service.
