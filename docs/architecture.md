# Vaultist architecture

## Boundaries

The Markdown Vault filesystem is authoritative. Only the Go server accesses it. Android sees domain models from `VaultRepository`; HTTP DTO parsing, URL construction, and settings persistence remain below that interface. Compose screens contain presentation and interaction logic but no filesystem or network operations.

The server is split into restrained packages:

- `internal/vault` validates normalized vault-relative paths and confines file opens to the configured root.
- `internal/markdown` uses Goldmark's syntax tree for standard Markdown structure and a stateful scanner for Markdown Vault wiki links. The scanner explicitly excludes fenced and inline code.
- `internal/index` owns refresh lifecycle, immutable snapshots, deterministic resolution, backlinks, revisions, and lazy file opens.
- `internal/search` matches notes by filename/title/alias (`files`) or note body text (`content`) against precomputed search blobs on the published snapshot.
- `internal/api` maps domain results to the versioned HTTP contract and structured safe errors.
- `internal/config` reads process configuration without hard-coding Linux host paths.

## Index and resolution

Each refresh walks non-hidden regular files once. Markdown bodies are bounded, hashed, parsed, and then released. Indexed note metadata includes stable ID, relative path, title, aliases, headings, link occurrences, attachments, modification data, size, revision, and a safe parse/read error. Image assets have path/name lookup tables and streaming metadata.

A replacement snapshot is assembled completely before an atomic publication. Requests keep using the last good snapshot during a refresh or temporary filesystem failure. The process health endpoint does not require a ready index. Refresh work checks context cancellation while walking and between notes.

Note resolution tries exact case-sensitive paths, unique case-insensitive paths, relative standard-Markdown paths, bare filenames, and frontmatter aliases. Candidate sets are sorted and deduplicated. More than one candidate is an explicit ambiguous result. Incoming backlinks are derived from every resolved outgoing note-link occurrence, retaining repeated references and source positions.

Asset resolution tries the current note directory, Markdown Vault root, configured attachment folder, and bare filename lookup. It never rescans during a request. Only PNG, JPEG, WebP, GIF, and SVG are indexed for serving in this release.

## Search

Search runs against the current immutable snapshot only; results reflect the last completed index generation until the next refresh.

At index build time each successfully indexed note gets precomputed lowercase search blobs: **files** (filename, title, aliases) and **content** (full note body). Queries are case-insensitive substring matches over those blobs in vault path order (`OrderedNoteIDs`). Search does not re-read note files from disk. Notes with index errors or oversized bodies are omitted from search blobs. Memory use scales with indexed note body size (bounded by the index read limit per note).

The stable Go interface is `internal/search.Search(ctx, snapshot, Request{Query, Mode})`. HTTP pagination (`limit`/`cursor`) and JSON mapping stay in `internal/api`. Server-side ranking and snippets are not exposed in HTTP v1.

## HTTP and caching

All routes live below `/api/v1`. Lists/searches have bounded limits and stable numeric cursors. Notes return quoted content-revision ETags and honor `If-None-Match`. Assets use weak metadata ETags and `http.ServeContent`, which supplies streaming and byte ranges. Responses are private-cache scoped.

Stable note IDs are normalized relative Markdown paths without `.md`; asset IDs are normalized relative paths. IDs may contain slash segments, Unicode, and spaces. They are URL encoded by Android and validated again by the server.

Search accepts `mode=files` (indexed filenames, titles, aliases) and `mode=content` (note body text against the current snapshot). The HTTP contract does not define an Ask mode. On Android, `SearchMode.Ask` is UI-only: Ask retrieval calls `/search` with `files` and `content` explicitly inside `VaultAskEngine`; `VaultistApi` must never send `mode=ask`.

Contract enforcement: OpenAPI is authoritative; server responses are validated against schemas in `internal/api/schema_test.go`; Android maps JSON in `ApiDtos.kt` with coverage in `ApiDtosTest.kt`. See [CONTRIBUTING.md](CONTRIBUTING.md).

Structured logs go to stdout as JSON (`log/slog`). Each request logs `method`, classified `route` (e.g. `/api/v1/notes/{id}` — never raw vault-relative IDs), `status`, `duration_ms`, and `error_code` when applicable. Index refresh logs `index_refresh_start`, `index_refresh_complete`, and `index_refresh_fail` with generation and counts. Search terms, note bodies, asset bytes, and vault paths are never logged.

## Android

The single-activity Compose client uses Hilt, immutable `StateFlow` states, lifecycle-aware collection, OkHttp cancellation, DataStore settings, Navigation Compose, Coil, and Material 2. Edge-to-edge setup and system-bar padding follow the shared activity-content pattern in `MainActivity`.

The Markdown presentation is native Compose. A bounded block parser creates headings, paragraphs, list items, quotes, and fenced-code blocks; inline presentation supports emphasis, strong text, code, standard links, wiki links, standard images, and wiki embeds. Server resolutions drive navigation and ambiguity/missing dialogs. Heading fragments and backlink source lines drive list positioning.

### Ask (on-device)

Ask lives under `ui/ask` (`AskViewModel`, `AskHint`, `AskResultsPane`). The browser screen still hosts the Files/Content/Ask mode toggle and shared search bar; `BrowserScreen` coordinates both ViewModels on mode changes, submit, and lifecycle resume.

Retrieval uses the existing host API only: the Ask engine analyzes the question, searches with both `files` and `content` modes, fuses and filters hits, loads candidate notes, packs passages, and prompts an on-device model through `PromptGenerationClient` (ML Kit GenAI). Citation validation keeps answers grounded in retrieved passages. `SearchMode.Ask` is a UI mode only; it is not sent as an HTTP search mode.

**Host retrieval contract (v1):** For each extracted subject term, Ask calls `GET /search?q={term}&mode=files` and `GET /search?q={term}&mode=content` (default `limit=100` from the Android client). It expects `SearchResponse.items` as note browse items with `kind=note`, `id`, `path`, `title`, and `name`. Hit **rank** is the zero-based index in `items`; fusion scoring is client-side, not server-side. Candidate note bodies come from `GET /notes/{id}`. Search result order must remain stable path order from the server; Ask does not depend on snippets or server ranking metadata.

Ask preferences (`enable_ask_thinking`) are stored separately from server URL settings, both in the same DataStore file. On-device Ask availability is gated through an injectable `OnDeviceAskEnabled` seam (currently backed by `BuildConfig.ENABLE_ON_DEVICE_ASK`).

Ask never mounts the vault filesystem. Host reachability, index readiness, and Tailscale HTTPS remain prerequisites for retrieval even though generation runs on the device.

## Editing readiness

The current implementation has no write endpoint. The seams already required for safe future editing are:

- a content hash revision on every note;
- original Markdown content rather than server-generated HTML;
- path-derived IDs rather than absolute paths;
- an Android repository boundary;
- separate read/refresh authorization methods ready to grow into write authorization;
- a replaceable snapshot and narrow vault file boundary; and
- an API error envelope able to add conflict details.

A later write service should perform conditional revision checks, write to a sibling temporary file, sync it, atomically replace the note, and refresh the index. Moves, attachments, and backlink-aware renames should be explicit vault operations rather than generic path writes.
