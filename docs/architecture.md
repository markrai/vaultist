# VaultView architecture

## Boundaries

The Markdown Vault filesystem is authoritative. Only the Go server accesses it. Android sees domain models from `VaultRepository`; HTTP DTO parsing, URL construction, and settings persistence remain below that interface. Compose screens contain presentation and interaction logic but no filesystem or network operations.

The server is split into restrained packages:

- `internal/vault` validates normalized vault-relative paths and confines file opens to the configured root.
- `internal/markdown` uses Goldmark's syntax tree for standard Markdown structure and a stateful scanner for Markdown Vault wiki links. The scanner explicitly excludes fenced and inline code.
- `internal/index` owns refresh lifecycle, immutable snapshots, deterministic resolution, backlinks, revisions, and lazy file opens.
- `internal/api` maps domain results to the versioned HTTP contract and structured safe errors.
- `internal/config` reads process configuration without hard-coding Linux host paths.

## Index and resolution

Each refresh walks non-hidden regular files once. Markdown bodies are bounded, hashed, parsed, and then released. Indexed note metadata includes stable ID, relative path, title, aliases, headings, link occurrences, attachments, modification data, size, revision, and a safe parse/read error. Image assets have path/name lookup tables and streaming metadata.

A replacement snapshot is assembled completely before an atomic publication. Requests keep using the last good snapshot during a refresh or temporary filesystem failure. The process health endpoint does not require a ready index. Refresh work checks context cancellation while walking and between notes.

Note resolution tries exact case-sensitive paths, unique case-insensitive paths, relative standard-Markdown paths, bare filenames, and frontmatter aliases. Candidate sets are sorted and deduplicated. More than one candidate is an explicit ambiguous result. Incoming backlinks are derived from every resolved outgoing note-link occurrence, retaining repeated references and source positions.

Asset resolution tries the current note directory, Markdown Vault root, configured attachment folder, and bare filename lookup. It never rescans during a request. Only PNG, JPEG, WebP, GIF, and SVG are indexed for serving in this release.

## HTTP and caching

All routes live below `/api/v1`. Lists/searches have bounded limits and stable numeric cursors. Notes return quoted content-revision ETags and honor `If-None-Match`. Assets use weak metadata ETags and `http.ServeContent`, which supplies streaming and byte ranges. Responses are private-cache scoped.

Stable note IDs are normalized relative Markdown paths without `.md`; asset IDs are normalized relative paths. IDs may contain slash segments, Unicode, and spaces. They are URL encoded by Android and validated again by the server.

## Android

The single-activity Compose client uses Hilt, immutable `StateFlow` states, lifecycle-aware collection, OkHttp cancellation, DataStore settings, Navigation Compose, and Coil. These are adapted from Voxidian's toolchain and organization conventions. Material 2 is retained to match Voxidian's current Compose BOM and theme approach. Edge-to-edge setup and system-bar padding follow Voxidian's shared activity-content pattern.

The Markdown presentation is native Compose. A bounded block parser creates headings, paragraphs, list items, quotes, and fenced-code blocks; inline presentation supports emphasis, strong text, code, standard links, wiki links, standard images, and wiki embeds. Server resolutions drive navigation and ambiguity/missing dialogs. Heading fragments and backlink source lines drive list positioning.

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
