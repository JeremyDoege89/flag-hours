# Open Labor Project API notes

How Flag Hours uses the Open Labor Project (OLP) API, and what we learned about it.
Attribution shown in the app: "Labor reference data: Open Labor Project".

## Basics
- Base URL: `https://openlaborproject.com/api/v1/...`
- Auth: header `x-api-key: <user's own key>`. The key is entered in Settings and stored encrypted (Android Keystore).
  A key is never bundled with the app.
- Free tier: **10 requests per day**, reset at **midnight UTC**.
- Response envelope: `{ "data": ..., "meta": ..., "error": { "code": "...", "message": "..." } }`.
  Branch on `error.code`, not only on the HTTP status.
- Rate-limit headers: `X-RateLimit-Remaining-Daily`, `X-RateLimit-Reset-Daily`.

## Endpoints

### `GET /vehicles`
| Params | Returns |
|---|---|
| none | makes |
| `make` | models / variants for that make |
| `make` + `model` | variants for that model |

The response shape isn't documented, so the app parses it defensively. Items may be strings or objects, and the app
reads `slug` / `id` / `value` plus `name` / `label` / `display`, and year fields if present.
Caching: the makes list is kept long-term (180 days), and models per make for 30 days.

### `GET /labor-times`
| Param | Required | Notes |
|---|---|---|
| `make` | yes | slug, e.g. `chevrolet` (not `chevy`) |
| `model` | yes | slug |
| `year` | yes | `YYYY` or `YYYY-YYYY` |
| `engine` | no | slug like `3.5l-v6-ecoboost`; **omit to get all engines** |
| `job` | no | **exact slug, not a search** |
| `min_confidence` | no | `medium`, `high` or `oem_verified` |

Each row has `job`, `jobSlug`, `category`, `hours`, `lowRange`, `highRange` and `confidence` (`estimated`, `high` or `oem_verified`).

### `GET /engines`
Builder tier only. On the free tier it returns `403 TIER_LOCKED`. **Not used.**

## Job naming (the root cause of "no information found")
`job` must match exactly. OLP names a job after the **part, with no verb**, for example:
"Radiator", "Radiator Cap", "Radiator Hose - Lower", "Radiator Hoses - Both", "Radiator Removal and Service",
"Thermostat", "Water Pump", "Brake Pads - Front", "Brake Rotors - Front Pair", "Alternator".
A 2017 F-150 3.5 EcoBoost has 493 jobs in 17 categories. A technician's "radiator replacement" never matches as a slug.
Wrong make/model/engine slugs (`chevy` instead of `chevrolet`, or a display name instead of `3.5l-v6-ecoboost`) also return 404.

## Errors
| HTTP | `error.code` | App message |
|---|---|---|
| 400 | `MISSING_PARAMS`, `INVALID_YEAR` | Vehicle details incomplete |
| 401 | `MISSING_API_KEY`, `INVALID_API_KEY` | No key / key rejected |
| 402 | `PAYMENT_REQUIRED` | Account needs payment |
| 403 | `TIER_LOCKED` | Not available on your plan |
| 403 | (HTML body) | Cloudflare challenge: the request was blocked before reaching the API |
| 404 | `NOT_FOUND` | Vehicle not in OLP (shows the slugs tried) |
| 429 | `RATE_LIMIT_EXCEEDED` | Daily limit hit (shows reset time, midnight UTC) |
| 429 | `BURST_LIMIT_EXCEEDED` | Too fast; wait `Retry-After` seconds |
| 500 | | OLP server error |

## How Flag Hours uses it (at most a few calls per vehicle)
1. **Resolve the vehicle.** VIN (typed, read from the RO scan, or scanned from the barcode) → NHTSA vPIC → year, make, model,
   displacement and cylinders. The make is mapped to an OLP slug using the cached makes list (`/vehicles`, 1 call, rarely
   repeated). The model is fuzzy-matched against the cached models for that make (`/vehicles?make=`, 1 call per make per 30 days).
   If the match is ambiguous the user picks; there's also a manual year/make/model picker.
2. **One `/labor-times` call per vehicle, without `job` or `engine`.** All rows are cached locally (`labor_rows`). If rows carry
   an engine, the engine is picked by displacement + cylinders (`3.5l-v6-...`); if that's ambiguous the user picks.
3. **Job matching is local and costs no calls.** `domain/JobMatcher` normalizes the technician's text (drops filler such as
   "replace", "R&R", "install"; expands abbreviations such as rad, t-stat, frt, lh) and scores it against `job` and `jobSlug`.
   The top 5 picks are shown, and a tapped pick is remembered on the job template so the next match is automatic.
4. Raw responses (URL without the key, status, rate-limit headers, body) are kept in a small debug log that can be shared
   from Settings.
