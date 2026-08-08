<p align="center">
  <img src="vaultist.png" alt="Vaultist" />
</p>

# What is this?

A lightweight Android companion for your Markdown vault, backed by a self-hosted Go server.

# Why?

Vaultist isn't trying to replace every Markdown application. It's trying to be the best companion to an existing Markdown vault.
Most Markdown apps try to do everything: editing, graph views, plugins, canvases, publishing, databases, and more. Vaultist takes a different approach. It focuses on what you do most often: reading your notes, making quick edits, and getting to your information quickly.

It also gives us the freedom to explore ideas that traditional editors rarely prioritize: thoughtful mobile interactions, features the community has requested for years, and workflows that stay out of your way instead of forcing you into them.

Vaultist is built on a simple belief: your notes should be yours, and the software that opens them should feel intuitive.

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
