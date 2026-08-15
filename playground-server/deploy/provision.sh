#!/usr/bin/env bash
#
# Provision a fresh Ubuntu VM to run the Jactl playground backend behind nginx.
#
# Idempotent enough to re-run. Intended to be run from the unpacked deployment
# archive (see `./gradlew :playground-server:deployTar`): unzip/untar it, then
#   cd jactl-playground-<version> && sudo CERTBOT_EMAIL=you@example.com ./provision.sh
# The jar defaults to the sibling ./jactl-playground.jar bundled in the archive.
#
# Prerequisites BEFORE running:
#   1. DNS: an A record api.jactl.io -> this VM's (reserved/static) public IP,
#      already resolving. Verify: dig +short api.jactl.io
#   2. Oracle Cloud: ports 80 and 443 opened in the VCN Security List / NSG for
#      the instance. (ufw below opens the host firewall, but Oracle's cloud
#      firewall is a SEPARATE layer and must be opened too, or nothing gets in.)
#
# Usage:
#   sudo CERTBOT_EMAIL=you@example.com ./provision.sh /path/to/jactl-playground.jar
#
set -euo pipefail

DEPLOY_DIR="$(cd "$(dirname "$0")" && pwd)"

DOMAIN="${DOMAIN:-api.jactl.io}"
APP_DIR="/opt/jactl-playground"
APP_USER="jactl-playground"
JAR_SRC="${1:-$DEPLOY_DIR/jactl-playground.jar}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-}"

if [[ $EUID -ne 0 ]]; then echo "Run as root (sudo)." >&2; exit 1; fi
if [[ ! -f "$JAR_SRC" ]]; then echo "Jar not found: $JAR_SRC" >&2; exit 1; fi

echo "==> Installing packages"
apt-get update
# Java 25 (LTS). openjdk-25-jre-headless is in recent Ubuntu (25.04+). On older LTS
# (e.g. 24.04) where that package isn't available, install Temurin 25 instead:
#   apt-get install -y wget apt-transport-https gpg
#   wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
#     | gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
#   echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb \
#     $(. /etc/os-release; echo $VERSION_CODENAME) main" > /etc/apt/sources.list.d/adoptium.list
#   apt-get update && apt-get install -y temurin-25-jre
apt-get install -y openjdk-25-jre-headless nginx ufw certbot python3-certbot-nginx

echo "==> Creating service user"
id -u "$APP_USER" >/dev/null 2>&1 || \
  useradd --system --no-create-home --shell /usr/sbin/nologin "$APP_USER"

echo "==> Installing application jar"
mkdir -p "$APP_DIR"
install -m 0644 "$JAR_SRC" "$APP_DIR/jactl-playground.jar"
chown -R "$APP_USER:$APP_USER" "$APP_DIR"

echo "==> Installing systemd unit"
install -m 0644 "$DEPLOY_DIR/systemd/jactl-playground.service" /etc/systemd/system/
systemctl daemon-reload
systemctl enable --now jactl-playground
systemctl --no-pager status jactl-playground | head -5 || true

echo "==> Installing nginx site (HTTP; certbot will add HTTPS)"
install -m 0644 "$DEPLOY_DIR/nginx/api.jactl.io.conf" /etc/nginx/sites-available/api.jactl.io.conf
ln -sf /etc/nginx/sites-available/api.jactl.io.conf /etc/nginx/sites-enabled/api.jactl.io.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl restart nginx

echo "==> Host firewall (ufw)"
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "==> Obtaining TLS certificate (Let's Encrypt via certbot --nginx)"
if [[ -z "$CERTBOT_EMAIL" ]]; then
  echo "!! CERTBOT_EMAIL not set — skipping TLS. Run manually once DNS is live:" >&2
  echo "   certbot --nginx -d $DOMAIN --agree-tos -m you@example.com --redirect" >&2
else
  certbot --nginx -d "$DOMAIN" --non-interactive --agree-tos -m "$CERTBOT_EMAIL" --redirect
fi

echo
echo "==> Done. Smoke test:"
echo "     curl -s http://127.0.0.1:8080/health        # service (localhost)"
echo "     curl -s https://$DOMAIN/health              # through nginx + TLS"
echo "     curl -s https://$DOMAIN/run -d '{\"script\":\"2+2\"}'"
