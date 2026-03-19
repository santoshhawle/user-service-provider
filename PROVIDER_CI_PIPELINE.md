# Provider Contract Tests - CI Pipeline Explained

## Overview

This GitHub Actions workflow verifies that the **user-service-provider** honours the contract defined by the **order-service-consumer**. It spins up a Pact Broker, publishes consumer-generated pacts, runs provider verification, and performs a `can-i-deploy` safety check.

---

## Line-by-Line Explanation

### Workflow Metadata (Lines 1-8)

```yaml
name: Provider Contract Tests
```
**Line 1** - Sets the display name of this workflow as it appears in the GitHub Actions UI.

```yaml
on:
  push:
    branches: [master, main]
  pull_request:
    branches: [master, main]
  workflow_dispatch:
```
**Lines 3-8** - Defines when this workflow runs:
- `push` - Triggers on every push to `master` or `main` branches.
- `pull_request` - Triggers when a PR is opened or updated against `master` or `main`.
- `workflow_dispatch` - Allows manual triggering from the GitHub Actions UI ("Run workflow" button).

---

### Job Definition (Lines 10-13)

```yaml
jobs:
  provider-verification:
    name: Provider Pact Verification
    runs-on: ubuntu-latest
```
- **Line 10** - `jobs:` begins the jobs section. A workflow can have multiple jobs.
- **Line 11** - `provider-verification` is the job ID (used internally for references/dependencies).
- **Line 12** - `name:` sets the human-readable job name shown in the GitHub Actions UI.
- **Line 13** - `runs-on: ubuntu-latest` specifies the runner environment. The job executes on the latest Ubuntu virtual machine provided by GitHub.

---

### Step 1: Checkout Provider (Lines 16-17)

```yaml
- name: Checkout Provider
  uses: actions/checkout@v4
```
Clones the **provider repository** (the repo where this workflow file lives) into the runner's workspace. This gives the workflow access to the provider's source code, `pom.xml`, and test files.

---

### Step 2: Checkout Consumer (Lines 19-23)

```yaml
- name: Checkout Consumer
  uses: actions/checkout@v4
  with:
    repository: santoshhawle/order-service-consumer
    path: order-service-consumer
```
Clones the **consumer repository** into a subdirectory called `order-service-consumer/`. This is needed because:
- The consumer's pact tests generate the contract JSON files.
- The consumer's Maven Pact plugin is configured to publish pacts to the broker.
- `repository:` specifies a different repo than the current one.
- `path:` places it in a subdirectory to avoid overwriting the provider checkout.

---

### Step 3: Set up JDK 17 (Lines 25-30)

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    java-version: '17'
    distribution: 'temurin'
    cache: 'maven'
```
- Installs **JDK 17** (Eclipse Temurin distribution) on the runner.
- `cache: 'maven'` caches the `~/.m2/repository` directory between workflow runs to speed up Maven dependency downloads.
- Both consumer and provider use Spring Boot 4.0.3 which requires Java 17.

---

### Step 4: Start Pact Broker (Lines 32-63)

This is the most complex step. It sets up the Pact Broker infrastructure using Docker.

#### Create Docker Network (Line 34)
```yaml
docker network create pact-network
```
Creates an isolated Docker network so the Postgres and Pact Broker containers can communicate by container name (DNS resolution).

#### Start PostgreSQL (Lines 35-39)
```yaml
docker run -d --name postgres --network pact-network \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=postgres \
  postgres:15
```
- `-d` runs the container in detached (background) mode.
- `--name postgres` assigns the container name, also used as the hostname on the Docker network.
- `--network pact-network` attaches to the custom network.
- `-e` sets environment variables to configure the database credentials.
- `postgres:15` is the Docker image (PostgreSQL version 15).

#### Wait for PostgreSQL (Lines 40-43)
```yaml
echo "Waiting for Postgres..."
until docker exec postgres pg_isready -U postgres > /dev/null 2>&1; do
  sleep 2
done
echo "Postgres is ready!"
```
Polls PostgreSQL using `pg_isready` every 2 seconds until it accepts connections. This ensures Postgres is ready before starting the Pact Broker.

#### Start Pact Broker (Lines 45-53)
```yaml
docker run -d --name pact-broker --network pact-network \
  -p 9292:9292 \
  -e PACT_BROKER_DATABASE_ADAPTER=postgres \
  -e PACT_BROKER_DATABASE_HOST=postgres \
  -e PACT_BROKER_DATABASE_USERNAME=postgres \
  -e PACT_BROKER_DATABASE_PASSWORD=postgres \
  -e PACT_BROKER_DATABASE_NAME=postgres \
  -e PACT_BROKER_PUBLIC_HEARTBEAT=true \
  pactfoundation/pact-broker
```
- `-p 9292:9292` maps port 9292 from the container to the host, making the broker accessible at `http://localhost:9292` from workflow steps.
- `PACT_BROKER_DATABASE_HOST=postgres` uses the container name as hostname (resolved via Docker network DNS).
- `PACT_BROKER_PUBLIC_HEARTBEAT=true` makes the `/diagnostic/status/heartbeat` endpoint publicly accessible without authentication (required for health checks).

#### Wait for Pact Broker (Lines 54-63)
```yaml
echo "Waiting for Pact Broker..."
for i in $(seq 1 30); do
  if curl -s -o /dev/null -w "%{http_code}" http://localhost:9292/diagnostic/status/heartbeat | grep -q "200"; then
    echo "Pact Broker is ready!"
    break
  fi
  echo "Attempt $i/30 - waiting..."
  sleep 5
done
curl -f http://localhost:9292/diagnostic/status/heartbeat || (echo "Pact Broker failed to start" && docker logs pact-broker && exit 1)
```
- Polls the heartbeat endpoint up to 30 times (5-second intervals = max 150 seconds).
- `curl -s -o /dev/null -w "%{http_code}"` silently checks the HTTP status code.
- The final `curl -f` acts as a hard fail: if the broker still isn't ready after all retries, it prints the container logs for debugging and exits with error.

---

### Step 5: Generate Consumer Pacts (Lines 65-67)

```yaml
- name: Generate Consumer Pacts
  working-directory: order-service-consumer
  run: mvn -B test
```
- `working-directory:` changes into the consumer's checkout directory.
- `mvn -B test` runs consumer Pact tests in batch mode (`-B` = non-interactive).
- The `ConsumerPactTest.java` uses `@ExtendWith(PactConsumerTestExt.class)` which generates a pact JSON file at `target/pacts/order_service_consumer-UserService.json`.

---

### Step 6: Publish Pacts to Broker (Lines 69-71)

```yaml
- name: Publish Pacts to Broker
  working-directory: order-service-consumer
  run: mvn -B pact:publish
```
- Uses the Pact Maven plugin (`au.com.dius.pact.provider:maven:4.6.15`) configured in the consumer's `pom.xml`.
- Publishes the generated pact files from `target/pacts/` to the Pact Broker at `http://localhost:9292`.
- This makes the contracts available for the provider to verify against.

---

### Step 7: Run Provider Verification (Lines 73-78)

```yaml
- name: Run Provider Verification
  run: |
    mvn -B test \
      -Dpact.verifier.publishResults=true \
      -Dpact.provider.version=${{ github.sha }} \
      -Dpact.provider.tag=${{ github.ref_name }}
```
- Runs the provider's `UserServiceProviderTest.java` which has `@PactBroker(url="http://localhost:9292")`.
- The test starts the Spring Boot application on a random port, fetches pacts from the broker, and replays each interaction against the real provider.
- `-Dpact.verifier.publishResults=true` publishes verification results back to the broker.
- `-Dpact.provider.version=${{ github.sha }}` tags the provider version with the Git commit SHA for traceability.
- `-Dpact.provider.tag=${{ github.ref_name }}` tags with the branch name (e.g., `master`, `feature/xyz`).
- `${{ github.sha }}` and `${{ github.ref_name }}` are GitHub Actions context variables injected at runtime.

---

### Step 8: Can I Deploy? (Lines 80-87)

```yaml
- name: Can I Deploy?
  run: |
    docker run --rm --network pact-network \
      pactfoundation/pact-cli \
      pact-broker can-i-deploy \
      --pacticipant UserService \
      --version ${{ github.sha }} \
      --broker-base-url http://pact-broker:9292
```
- Uses the `pactfoundation/pact-cli` Docker image which contains the `pact-broker` CLI tool.
- `--rm` removes the container after execution.
- `--network pact-network` connects to the Docker network so it can reach the broker via hostname `pact-broker`.
- `can-i-deploy` queries the Pact Broker to check whether it is safe to deploy `UserService` at the given version.
- It checks that all consumer contracts have been successfully verified by this provider version.
- If verification passed, exit code is `0` (success). If not, exit code is `1` (failure), blocking the pipeline.
- `--pacticipant UserService` must match the `@Provider("UserService")` annotation in the test.

---

### Step 9: Stop Containers (Lines 89-93)

```yaml
- name: Stop Containers
  if: always()
  run: |
    docker rm -f pact-broker postgres 2>/dev/null || true
    docker network rm pact-network 2>/dev/null || true
```
- `if: always()` ensures this cleanup step runs regardless of whether previous steps passed or failed.
- `docker rm -f` force-removes the containers (stops them if running).
- `docker network rm` removes the custom network.
- `2>/dev/null || true` suppresses errors if containers/network don't exist (idempotent cleanup).

---

## Pipeline Flow Diagram

```
Checkout Provider & Consumer
            |
      Set up JDK 17
            |
  Start Postgres + Pact Broker
            |
   Generate Consumer Pacts
     (mvn test on consumer)
            |
   Publish Pacts to Broker
     (mvn pact:publish)
            |
  Run Provider Verification
  (mvn test on provider with
   results published to broker)
            |
      Can I Deploy?
  (pact-broker can-i-deploy)
            |
     Stop Containers
      (always runs)
```
