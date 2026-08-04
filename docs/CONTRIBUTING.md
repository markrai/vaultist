# Contributing to Vaultist

Short expectations for keeping the tree maintainable. See [architecture.md](architecture.md) for design boundaries and [api/openapi.yaml](../api/openapi.yaml) for the HTTP contract.

## Ownership

| Area | Home | Do not grow |
|---|---|---|
| HTTP contract | `api/openapi.yaml` | Hand-written clients/handlers without updating OpenAPI |
| Server path confinement | `server/internal/vault` | Path logic in `api` or `index` |
| Markdown dialect (index) | `server/internal/markdown` | Wiki/link rules duplicated ad hoc in handlers |
| Index / resolution | `server/internal/index` | Snapshot mutation from request handlers |
| HTTP mapping | `server/internal/api` | Vault filesystem walks outside refresh/search seams |
| Vault search | `server/internal/search` | Search matching in HTTP handlers |
| Android domain models | `android/.../domain` | Network/JSON types leaking into screens |
| Vault I/O on Android | `VaultRepository` / `data/api` | OkHttp or JSON parsing in Composables |
| Ask orchestration | `data/ask`, `data/genai`, `ui/ask` | New Ask product logic in `BrowserViewModel` / `BrowserScreen` |

Prefer a new package or ViewModel over extending `BrowserViewModel`, `BrowserScreen`, `internal/api/api.go`, or `internal/index/index.go` when a second concern appears.

## Change habits

- Behavior that crosses a process or package boundary updates `docs/architecture.md`.
- Operator-facing paths, versions, and feature lists update `README.md`.
- API shape changes update OpenAPI in the same change as handlers and Android DTOs.
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
