# Contributing to Vaultist

Short expectations for keeping the tree maintainable. See [architecture.md](architecture.md) for design boundaries and [api/openapi.yaml](../api/openapi.yaml) for the HTTP contract.

## Ownership

| Area | Home | Do not grow |
|---|---|---|
| HTTP contract | `api/openapi.yaml` | Hand-written clients/handlers without updating OpenAPI |
| Server path confinement | `server/internal/vault` | Path logic in `api` or `index` |
| Markdown dialect (index) | `server/internal/markdown` | Wiki/link rules duplicated ad hoc in handlers; see [markdown-dialect.md](markdown-dialect.md) |
| Index / resolution | `server/internal/index` | Snapshot mutation from request handlers |
| HTTP mapping | `server/internal/api` | Vault filesystem walks outside refresh/search seams |
| Vault search | `server/internal/search` | Search matching in HTTP handlers |
| Android domain models | `android/.../domain` | Network/JSON types leaking into screens |
| Vault I/O on Android | `VaultRepository` / `data/api` | OkHttp or JSON parsing in Composables |
| Ask orchestration | `data/ask`, `data/genai`, `ui/ask` | New Ask product logic in `BrowserViewModel` / `BrowserScreen` |

Prefer a new package or ViewModel over extending `BrowserViewModel`, `BrowserScreen`, `internal/api/api.go`, or `internal/index/index.go` when a second concern appears.

## API contract

**Authoritative spec:** [`api/openapi.yaml`](../api/openapi.yaml)

**Strategy:** hand-mapped DTOs (no codegen).

- **Server:** handlers map index/domain results to JSON (`map[string]any` today). No Go client/server codegen.
- **Android:** [`ApiDtos.kt`](../android/app/src/main/java/com/markrai/vaultist/data/api/ApiDtos.kt) parses `JSONObject` into [`domain/Models.kt`](../android/app/src/main/java/com/markrai/vaultist/domain/Models.kt). No Kotlin codegen.
- **Enforcement:** OpenAPI schema validation in [`server/internal/api/schema_test.go`](../server/internal/api/schema_test.go) plus Android [`ApiDtosTest.kt`](../android/app/src/test/java/com/markrai/vaultist/data/api/ApiDtosTest.kt). CI also lints OpenAPI with Redocly.

Revisit codegen only if write endpoints multiply or dual-language drift becomes painful.

### API shape change checklist

When changing request or response JSON:

1. Update [`api/openapi.yaml`](../api/openapi.yaml) first (or in the same PR).
2. Update server handlers in `server/internal/api`.
3. Update Android `ApiDtos.kt` and `domain/Models.kt`.
4. Extend `ApiDtosTest.kt` for new or required fields.
5. Run `go test ./internal/api/...` from `server/` (schema tests must pass).
6. Run `go test ./internal/api/...` from `server/` (schema tests must pass).
7. Run `.\gradlew.bat :app:testDebugUnitTest` from `android/`.

For write endpoints, also extend handler tests (`api_write_test.go`) and Android MockWebServer repository tests.

PRs that change handlers or DTOs without updating OpenAPI should not merge.

## Markdown dialect

**Authoritative spec:** [markdown-dialect.md](markdown-dialect.md)

- **Server index parser:** `server/internal/markdown` — links, slugs, attachments.
- **Android display:** `android/.../ui/markdown` — block layout and rendering; uses server `Note.links` for resolution.
- **Shared fixtures:** `fixtures/markdown/` — update when dialect rules change; run Go and Android dialect tests.

The Android display parser must not grow index responsibilities (no wiki scanner in Kotlin).

## Android config and build

**Version catalog:** [`android/gradle/libs.versions.toml`](../android/gradle/libs.versions.toml) — bump AGP, Kotlin, Compose BOM, and library versions here.

**Runtime config (Hilt):** [`di/config/`](../android/app/src/main/java/com/markrai/vaultist/di/config/) — `NetworkConfig`, `AskRuntimeConfig`, `BrowseUiConfig`. Inject these instead of hardcoding timeouts and Ask budgets in engines or ViewModels.

**Gradle modules:** stay single-module (`:app`) until boundaries are stable, the catalog is in place, and config is injectable at module seams. Only then consider `:feature-ask`, `:core-model`, etc.

## Change habits

- Behavior that crosses a process or package boundary updates `docs/architecture.md`.
- Operator-facing paths, versions, and feature lists update `README.md`.
- API shape changes follow the checklist above.
- Keep PRs aligned to one boundary (docs sync ≠ Ask extract ≠ index split).

## Local checks

From `server`:

```powershell
go test ./...
go vet ./...
```

From `android`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug
```
