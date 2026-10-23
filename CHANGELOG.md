# Changelog

Every release of `com.github.witanmarkets:witan-sdk-java`, newest first, grouped the way
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) groups them.

## 0.1.1 — 2026-10-23

### Fixed

- JitPack builds it: `maven-compiler-plugin` is pinned to 3.13.0, because the default one of the Maven that JitPack runs ignores `maven.compiler.release` and compiled for Java 5. No code changed; 0.1.0 never built on JitPack.

## 0.1.0 — 2026-10-23

### Added

- The first release: `searchError`, `search`, `read`, `datasets`, `dataset`, `datasetData`, `query`, `quota` and
  `credits`, on the JDK's HttpClient (Java 11+) and Jackson; usable from Scala and Kotlin.
- `Witan.builder()` with `apiKey`, `baseUrl`, `userAgent`, `retries`, `timeout`, `httpClient`; `WITAN_API_KEY` and
  `WITAN_BASE_URL` as fallbacks.
- `WitanException` for every failed call and `PaymentRequiredException` for a 402, with the price and the x402
  URL. Network errors, 429, 502, 503 and 504 are retried twice, honouring `Retry-After`.
