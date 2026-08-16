# BolKe language service

The Android keyboard calls the private service only when `languageServiceUrl` is set at build time:

```text
./gradlew assembleRelease -PlanguageServiceUrl=https://your-service.example/v1/punglish
```

## Request

```json
{"text":"ਕੀ ਹਾਲ ਆ ਤੂੰ ਕਿੱਥੇ ਓ", "output":"punglish"}
```

## Response

```json
{"punglish":"ki haal aa, tu kithe o?"}
```

The service must not log request text, retain it, or use it for model training. It should return within five seconds. On an unavailable or invalid response, the keyboard uses its local Punjabi normalizer instead.
