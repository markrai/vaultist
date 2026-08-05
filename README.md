<p align="center">
  <img src="vaultist.png" alt="Vaultist" />
</p>

# Vaultist

Vaultist is a Markdown Vault viewer made of two independently buildable applications:

- a Kotlin/Jetpack Compose Android client; and
- a small Go HTTP server that indexes a locally mounted Markdown Vault on the Linux host.

The Android app talks only to the versioned JSON API. It never mounts SMB, scans the remote filesystem, or requests broad storage access. Tailscale Serve supplies private HTTPS transport between the device and the Linux host.

```text
Android Vaultist
        |
        | HTTPS over Tailscale
        v
Tailscale Serve on the Linux host
        |
        | loopback HTTP
        v
Vaultist Go server
        |
        | local vault filesystem
        v
/srv/Vault
```

## Repository layout

```text
android/             Compose client and its independent Gradle wrapper
server/              Go server, index, parser, HTTP handlers, and Dockerfile
api/openapi.yaml     Authoritative API contract
deploy/              Linux host Docker Compose configuration and example environment
docs/architecture.md Design boundaries and editing-readiness notes
docs/CONTRIBUTING.md Package ownership and change expectations
.github/workflows/   Path-filtered Android and server checks
```

## Current behavior

The app provides server setup/validation, folder browsing, filename/title/alias search, note body (content) search, on-device Ask over retrieved notes, note viewing and editing, note create/delete when the vault is writable, wiki-link navigation, explicit missing/ambiguous-link presentation, backlinks, index refresh, inline images, and a zoomable image viewer. It preserves screen state in ViewModels and uses cancellable OkHttp calls.

The server performs a full initial scan outside the request path and publishes an immutable index snapshot. Refreshes build a replacement snapshot and keep the last good snapshot if a temporary filesystem failure occurs. Note content is read lazily, assets are streamed with HTTP range support, and neither full bodies nor Markdown ASTs are retained in the index. At index time each successfully indexed note gets precomputed lowercase search blobs for **files** (filename, title, aliases) and **content** (note body); query matching is a case-insensitive substring search over those blobs and does not re-read note files from disk.

Supported syntax includes headings, paragraphs, ordered/unordered lists, blockquotes, emphasis, strong emphasis, inline/fenced code, standard links/images, and these Markdown Vault wiki-link forms:

```markdown
[[Note]]
[[Folder/Note]]
[[Note|Display text]]
[[Note#Heading]]
[[Note#Heading|Display text]]
![[image.png]]
![[attachments/image.png]]
![[Note]]
![[Note#Heading]]
![Alt text](attachments/image.png)
```

Wiki-looking content inside inline or fenced code is inactive. Frontmatter reads only `title` and scalar/list `aliases`. Exact vault paths win, case-insensitive matches are accepted only when unique, and ambiguous bare names or aliases return every candidate instead of selecting one.

## Server development

Requirements: Go 1.23 or newer. The server requires `VAULT_ROOT`; the listen address defaults to `127.0.0.1:8080`. The Go module path is `github.com/markrai/vaultist/server`.

Windows PowerShell against the mapped Markdown Vault:

```powershell
$env:VAULT_ROOT='O:\'; $env:VAULT_NAME='Vault'; go run .\cmd\vaultist-server
```

Run that command from `server`. The status endpoint is `http://127.0.0.1:8080/api/v1/status`.

Verification:

```powershell
go test ./...
go test -race ./...
go vet ./...
go build -trimpath -ldflags='-s -w' ./cmd\vaultist-server
```

## Android development

Toolchain pins live in [`android/gradle/libs.versions.toml`](android/gradle/libs.versions.toml): AGP 8.9.1, Kotlin 2.0.21, Gradle 8.11.1, Java 17, compile/target SDK 36, minimum SDK 26, Material 2, Compose BOM `2024.03.00`. Open `android` as the Android Studio project or use its wrapper directly.

The emulator development URL is `http://10.0.2.2:8080`; Android emulator `localhost` addresses the emulator itself. Cleartext is restricted to emulator/loopback development hosts. Tailnet and other remote server URLs must use HTTPS.

Windows PowerShell verification from `android`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin
```

Instrumentation execution additionally requires a running emulator or physical device:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Linux host deployment

1. Copy or check out the project on the Linux host.
2. Review `deploy/example.env` and create `deploy/.env` if desired.
3. Confirm `VAULT_HOST_PATH` (default `/srv/Vault`) is the intended Markdown Vault on the host and is writable by the vault directory owner (`deploy.sh` runs the container as that UID/GID).
4. From `deploy`, run `docker compose up -d --build`.
5. Confirm `curl http://127.0.0.1:8080/api/v1/status` returns JSON.
6. Expose that loopback service to the tailnet with Tailscale Serve.

With a current Tailscale client, the documented background reverse-proxy form is:

```bash
sudo tailscale serve --bg http://127.0.0.1:8080
tailscale serve status
```

Tailscale changed Serve CLI syntax in client 1.52, so verify the command against the current [official Serve command reference](https://tailscale.com/docs/reference/tailscale-cli/serve) before applying it on the Linux host. Do not use Tailscale Funnel: Funnel is public exposure, while Vaultist is designed for tailnet-only Serve access.

The container binds only to host loopback, runs as the vault directory owner (UID/GID from `deploy.sh` or `VAULT_UID`/`VAULT_GID` in `deploy/.env`), drops Linux capabilities, uses a read-only root filesystem, and mounts `${VAULT_HOST_PATH}:/vault:rw` (default `/srv/Vault`) so note edits can be saved.

## Security model

The initial authorization boundary trusts a correctly configured tailnet and its ACL/grant policy. The server is not safe to expose directly to the public internet merely because its URL is hard to guess. Tailscale Serve terminates private HTTPS; the Go process receives loopback HTTP. No application user database or bearer secret is added in this release.

Read and refresh authorization are isolated behind a server interface so future write authorization can be stricter. The server validates all public IDs, rejects traversal, ignores symlinks and hidden implementation directories, and never returns its absolute vault root or raw internal errors.

## Scope and roadmap

Note body editing is supported via conditional `PUT /api/v1/notes/{id}` with `If-Match` revision checks. New notes can be created with `POST /api/v1/notes` from the browser `+` action when the vault is writable. Notes can be deleted with conditional `DELETE`. There are no uploads, moves, offline write queues, synchronization, graph/canvas views, Dataview evaluation, plugin execution, collaboration, analytics, or telemetry. Search covers filenames/titles/aliases (`mode=files`) and note body text (`mode=content`). Ask answers on-device after retrieving notes through those search modes; it is not a separate HTTP search mode.

Future work: attachment CRUD, renames with backlink updates. Writes use vault-concept endpoints with atomic replacement — not last-writer-wins or unrestricted filesystem access.

See [docs/architecture.md](docs/architecture.md), [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md), and [api/openapi.yaml](api/openapi.yaml) for the detailed design and contract.

## License

Vaultist is licensed under the GNU Affero General Public License version 3; see [LICENSE](LICENSE).
