#!/usr/bin/env bash
# Runs frontend (Node) and backend (Maven/Surefire) tests, outputting TAP for onp-spec verify.
set -uo pipefail

# --- 1a. Frontend — unit tests (Node.js TAP) ---
node --test --test-reporter=tap "client/src/**/*.test.ts" 2>&1 || true

# --- 1b. Frontend — component tests (Vitest → TAP) ---
vitest_out=$(cd client && bunx vitest run --reporter=verbose 2>&1) || true
tap_vt=200
echo "$vitest_out" | grep '@spec:' | while IFS= read -r line; do
  tap_vt=$((tap_vt + 1))
  title=$(echo "$line" | sed 's/.*@spec:/\@spec:/' | sed 's/[[:space:]]*$//')
  if echo "$line" | grep -q '✓\|✔'; then
    echo "ok $tap_vt - $title"
  else
    echo "not ok $tap_vt - $title"
  fi
done

# --- 2. Backend (Maven → TAP) ---
export JAVA_HOME="/opt/homebrew/opt/openjdk@25"
export PATH="$JAVA_HOME/bin:$PATH"
export DOCKER_HOST="${DOCKER_HOST:-unix://$HOME/.colima/default/docker.sock}"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE="${TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE:-/var/run/docker.sock}"

cd server
./mvnw test -Dsurefire.useFile=true -q 2>/dev/null || true
cd ..

# Build method→spec mapping from Java source (// @spec:AC-xxx above @Test)
# Supports multiple @spec tags per test method (one per comment line)
SPEC_MAP=$(mktemp)
for src in $(find server/src/test/java/com/example/carboncalculator -name '*.java' 2>/dev/null); do
  [ -f "$src" ] || continue
  prev_specs=""
  while IFS= read -r line; do
    if echo "$line" | grep -qoE '@spec:AC-[0-9]+'; then
      ac=$(echo "$line" | grep -oE 'AC-[0-9]+')
      prev_specs="${prev_specs:+$prev_specs }$ac"
    elif echo "$line" | grep -q 'void .*()'  && [ -n "$prev_specs" ]; then
      method=$(echo "$line" | sed -n 's/.*void \([a-zA-Z0-9_]*\)().*/\1/p')
      if [ -n "$method" ]; then
        for spec_id in $prev_specs; do
          echo "$method $spec_id" >> "$SPEC_MAP"
        done
      fi
      prev_specs=""
    else
      stripped=$(echo "$line" | sed 's/^[[:space:]]*//')
      case "$stripped" in
        @*|//*)  ;; # keep prev_specs for annotations and comments
        "")      ;; # keep for blank lines
        *)       prev_specs="" ;; # reset for other lines
      esac
    fi
  done < "$src"
done

# Parse surefire XML reports and emit TAP for @spec-tagged tests
# Supports methods mapped to multiple spec IDs (one TAP line per spec ID)
tap_index=100
for xml in server/target/surefire-reports/TEST-*.xml; do
  [ -f "$xml" ] || continue
  while IFS= read -r tc_line; do
    # Extract the FIRST name= attribute value (not classname=)
    method_name=$(echo "$tc_line" | grep -o 'name="[^"]*"' | head -1 | sed 's/name="//;s/"//')
    [ -z "$method_name" ] && continue
    spec_ids=$(grep "^$method_name " "$SPEC_MAP" | awk '{print $2}')
    [ -z "$spec_ids" ] && continue

    # Determine pass/fail once for this testcase
    tc_passed=true
    if echo "$tc_line" | grep -q '/>$'; then
      tc_passed=true
    else
      while IFS= read -r inner; do
        echo "$inner" | grep -q '<failure\|<error' && tc_passed=false
        echo "$inner" | grep -q '</testcase>' && break
      done
    fi

    # Emit one TAP line per spec ID
    for spec_id in $spec_ids; do
      tap_index=$((tap_index + 1))
      if $tc_passed; then
        echo "ok $tap_index - @spec:$spec_id $method_name"
      else
        echo "not ok $tap_index - @spec:$spec_id $method_name"
      fi
    done
  done < "$xml"
done

rm -f "$SPEC_MAP"
