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
| Create-note orchestration | `ui/create` (`CreateNoteViewModel`) | Create logic in `BrowserViewModel` / `BrowserScreen` |
| Browse sync after writes | `ui/browser` (`PendingBrowseSync`, `BrowserViewModel` merge/reconcile) | Ad hoc optimistic inserts without pending merge; reconcile on every note return |
| Note GET before index catch-up | `server/internal/index` (`GetNoteForRead`), `server/internal/api/notes_read.go` | Requiring snapshot membership for readable on-disk notes |
| Index refresh coalescing | `server/internal/index/manager.go` | Dropping `StartRefresh` while indexing without scheduling follow-up |
| Home-screen widgets | `ui/widget`, `data/widget` | Widget logic in `BrowserViewModel` / `BrowserScreen` / `NoteScreen`; OkHttp in Glance UI |

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
6. Run `.\gradlew.bat :app:testDebugUnitTest` from `android/`.

For write endpoints, also extend handler tests (`api_write_test.go`) and Android MockWebServer repository tests.

PRs that change handlers or DTOs without updating OpenAPI should not merge.

### Write / browse sync checklist

When changing create, save, delete, folder create/delete, browse return paths, or note GET/refresh behavior, read [Stale index, writes, and client sync](architecture.md#stale-index-writes-and-client-sync) and verify:

1. **Server:** write paths call coalescing `StartRefresh`; `GetNoteForRead` serves unindexed on-disk notes; GET `revision` matches disk bytes.
2. **Android:** every write offers `BrowseMutation` via `PendingBrowseSync`; `loadBrowse` merges pending upserts/tombstones.
3. **Android:** `onReturnedToBrowse` reconciles browse **only for deletes**, not upsert-only returns from the note screen.
4. **Tests:** `go test ./internal/index/... ./internal/api/...` and `BrowserViewModelTest`, `NoteViewModelTest`, `CreateNoteViewModelTest`.

## Markdown dialect

**Authoritative spec:** [markdown-dialect.md](markdown-dialect.md)

- **Server index parser:** `server/internal/markdown` — links, slugs, attachments.
- **Android display:** `android/.../ui/markdown` — block layout and rendering; uses server `Note.links` for resolution.
- **Shared fixtures:** `fixtures/markdown/` — update when dialect rules change; run Go and Android dialect tests.

The Android display parser must not grow index responsibilities (no wiki scanner in Kotlin).

## Server development

Requirements: Go 1.23 or newer. The server requires `VAULT_ROOT`; the listen address defaults to `127.0.0.1:8080`. The Go module path is `github.com/markrai/vaultist/server`.

Windows PowerShell against the mapped Markdown Vault:

```powershell
$env:VAULT_ROOT='O:\'; $env:VAULT_NAME='Vault'; go run .\cmd\vaultist-server
```

Run that command from `server`. The status endpoint is `http://127.0.0.1:8080/api/v1/status`.

## Android config and build

**Version catalog:** [`android/gradle/libs.versions.toml`](../android/gradle/libs.versions.toml) — bump AGP, Kotlin, Compose BOM, and library versions here. Toolchain pins: AGP 8.9.1, Kotlin 2.0.21, Gradle 8.11.1, Java 17, compile/target SDK 36, minimum SDK 26, Material 2, Compose BOM `2024.03.00`. Open `android` as the Android Studio project or use its wrapper directly.

**Runtime config (Hilt):** [`di/config/`](../android/app/src/main/java/com/markrai/vaultist/di/config/) — `NetworkConfig`, `AskRuntimeConfig`, `BrowseUiConfig`. Inject these instead of hardcoding timeouts and Ask budgets in engines or ViewModels.

**Gradle modules:** stay single-module (`:app`) until boundaries are stable, the catalog is in place, and config is injectable at module seams. Only then consider `:feature-ask`, `:core-model`, etc.

**Development URLs:** the emulator development URL is `http://10.0.2.2:8080`; Android emulator `localhost` addresses the emulator itself. Cleartext is restricted to emulator/loopback development hosts. Tailnet and other remote server URLs must use HTTPS.

## Change habits

- Behavior that crosses a process or package boundary updates `docs/architecture.md`.
- Operator-facing paths, versions, and feature lists update `README.md`.
- API shape changes follow the checklist above.
- Keep PRs aligned to one boundary (docs sync ≠ Ask extract ≠ index split).

## Local checks

From `server`:

```powershell
go test ./...
go test -race ./...
go vet ./...
go build -trimpath -ldflags='-s -w' ./cmd\vaultist-server
```

From `android`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

Instrumentation execution additionally requires a running emulator or physical device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```
