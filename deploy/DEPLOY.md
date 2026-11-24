# Deploying PayCore on Oracle Cloud (Always Free)

The whole stack (PayCore, PostgreSQL, Redis, Kafka, TigerBeetle, Caddy) runs on one free
Ampere A1 VM with Docker Compose. Caddy serves HTTPS with a free Let's Encrypt certificate.

## 1. Create the VM (Oracle Cloud console)

1. Sign up at cloud.oracle.com (a card is needed for verification; Always Free resources are not charged).
2. **Compute → Instances → Create instance**
   - Image: **Canonical Ubuntu 24.04**
   - Shape: **Ampere → VM.Standard.A1.Flex**, 2 OCPU, 12 GB memory (within the free limit)
   - Networking: keep "Assign a public IPv4 address" on
   - SSH keys: upload your public key (`~/.ssh/id_ed25519.pub`; create one with `ssh-keygen -t ed25519` if needed)
3. If you get "Out of capacity", try another availability domain or retry later.
4. Open web ports in Oracle's network firewall:
   **Instance → Subnet → Default Security List → Add Ingress Rules**:
   source `0.0.0.0/0`, TCP, destination port `80`; and again for `443`.

## 2. Prepare the server

```bash
ssh ubuntu@<PUBLIC_IP>
git clone https://github.com/bhoomigundecha/PayCore.git
cd PayCore
./deploy/setup-server.sh
exit
```

Log back in so the `docker` group applies.

## 3. Configure and deploy

```bash
ssh ubuntu@<PUBLIC_IP>
cd PayCore/deploy
cp .env.example .env
nano .env
```

- `PAYCORE_DOMAIN`: your IP with dashes, e.g. `140-238-12-34.sslip.io` (sslip.io is free DNS that resolves to the IP inside the name)
- `POSTGRES_PASSWORD`: output of `openssl rand -hex 24`

```bash
./deploy.sh
```

The first run builds the app image (a few minutes) and formats TigerBeetle. Then open
`https://<PAYCORE_DOMAIN>/`.

## Operating it

```bash
docker compose logs -f app            # application logs
docker compose ps                     # service status
git pull && ./deploy.sh               # deploy a new version
```

## Troubleshooting

- **`PermissionDenied` from tigerbeetle**: Docker blocked io_uring; the compose file already sets `seccomp=unconfined`.
- **tigerbeetle exits with code 137**: out of memory; give the VM more memory or lower `--cache-grid`.
- **No HTTPS certificate**: ports 80/443 must be open in both the Oracle security list and the VM (`setup-server.sh`). Check `docker compose logs caddy`.
- **App restarts at boot**: it waits for TigerBeetle; `restart: unless-stopped` retries until the ledger is reachable.
