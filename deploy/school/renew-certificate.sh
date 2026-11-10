#!/usr/bin/env bash
set -euo pipefail
cd /opt/paaweb
exec 9>/run/paaweb-certificate.lock
flock -n 9 || exit 0
if openssl x509 -checkend 2592000 -noout -in tls/fullchain.pem >/dev/null 2>&1; then
    exit 0
fi
# Challenge responses are public and must be readable by the nginx worker.
# Existing private keys remain mode 0600 in a mode 0700 directory.
umask 022
timeout 180 python3 tools/acme_tiny.py \
    --account-key tls/account.key --csr tls/domain.csr \
    --acme-dir acme-webroot/.well-known/acme-challenge > tls/fullchain.pem.new
openssl x509 -checkend 2592000 -noout -in tls/fullchain.pem.new
cp -p tls/fullchain.pem tls/fullchain.pem.previous
mv tls/fullchain.pem.new tls/fullchain.pem
if ! nginx -t; then
    cp -p tls/fullchain.pem.previous tls/fullchain.pem
    exit 1
fi
nginx -s reload
