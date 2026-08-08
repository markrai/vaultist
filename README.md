<p align="center">
  <img src="vaultist.png" alt="Vaultist" />
</p>

# What is this?

a 2-part app Markdown vault viewer & editor: an Android app, and a Go HTTP server which runs on a Linux host. Windows server coming soon!

# Why?

Invariably, this question gets asked within the open-source community: Why create another "Markdown editor" when so many already exist? The answer is a combination of reasons: Because the current offerings are too bloated with features, non-intuitive, or sometimes, simply too limited in capability.

What if a viewer could just be a viewer? A capable editor when called upon? What if it offered features which have been requested repeatedly, but seldom fulfilled? What if we could try out intuitive features, without being forced into them?

That's what Vaultist is about. Simple, incredibly capable, and unique in its own right.

# How is it different?    

Most Markdown vault viewers put the raw filesystem in front of the client - an SMB/WebDAV mount, direct local storage access, or a synced local mirror - and make every device walk, parse, and resolve the vault itself. Vaultist instead puts a stateful indexing server behind a narrow, versioned JSON API. Because only the Go server touches disk, link resolution, backlinks, aliases, and search are computed once against an immutable snapshot (so reads are never torn mid-refresh), edits go through conditional, atomically-applied writes rather than last-writer-wins, and HTTP brings ETags, conditional GETs, and range-based image streaming for free - all while the client stays thin and low-privilege, speaking nothing but `/api/v1`. The deliberate trade-offs are that Vaultist is online-only (no offline mirror) and requires running a single host process.

# Quick Start

1. Copy or git clone the project on the Linux host.
2. Review `deploy/example.env` and create `deploy/.env` 
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

## Development

Server and Android setup, toolchain pins, and verification commands live in [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md).


## License

Vaultist is licensed under the GNU Affero General Public License version 3; see [LICENSE](LICENSE).
