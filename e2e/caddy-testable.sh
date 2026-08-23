#!/bin/sh
# Run the PRODUCTION deploy/Caddyfile in the e2e stack.
#
# The point of this file is that the config under test is the real one. Only a
# single line is added — `local_certs` — because everything else about the
# production file works unchanged in the stack: the upstreams it names (web,
# admin, app, api) are the service names here too.
#
# Why that one line is unavoidable: production gets its certificates from
# Let's Encrypt, which cannot be reached from here and would not issue for
# these names anyway. `local_certs` swaps in Caddy's own internal CA and
# changes nothing else — the sites still listen on :443 over real TLS, so the
# tests exercise the same request path production does. That matters for HSTS
# in particular, which is a header about HTTPS.
#
# The compose service gives the container network aliases for each hostname,
# so the suite connects to https://cerebrozen.in/ with correct SNI rather than
# faking a Host header the TLS layer would not agree with.
#
# The transformation is then verified rather than trusted: strip the added line
# back out and the result must be byte-identical to the production file. If a
# future edit to this script starts rewriting site blocks, or quietly drops an
# `import security_headers` to make a test pass, the container refuses to start.
set -e

PROD=/etc/caddy/Caddyfile.prod
TEST=/etc/caddy/Caddyfile

sed 's|^\(\s*email .*\)$|\1\n\tlocal_certs|' "$PROD" > "$TEST"

grep -q 'local_certs' "$TEST" || {
  echo "caddy-testable: could not find the global options block to patch" >&2
  exit 1
}

grep -v 'local_certs' "$TEST" > /tmp/roundtrip
if ! diff -u "$PROD" /tmp/roundtrip; then
  echo "caddy-testable: the derived config differs from production by more" >&2
  echo "  than the one line it is allowed to add. Refusing to serve a config" >&2
  echo "  that would let the header tests pass against something we do not ship." >&2
  exit 1
fi

echo "caddy-testable: serving deploy/Caddyfile with local_certs, otherwise verbatim"
exec caddy run --config "$TEST" --adapter caddyfile
