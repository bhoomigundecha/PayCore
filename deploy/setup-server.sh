#!/usr/bin/env bash
# One-time setup for a fresh Oracle Cloud Ubuntu VM. Run as the default 'ubuntu' user.
set -euo pipefail

echo "==> Installing Docker"
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"

echo "==> Opening ports 80 and 443 in the VM firewall"
# Oracle's Ubuntu images ship iptables rules that reject everything except SSH.
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save

echo "==> Done. Log out and back in (so the docker group applies), then run deploy/deploy.sh"
