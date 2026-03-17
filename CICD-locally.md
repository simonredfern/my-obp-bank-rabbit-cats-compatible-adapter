# Local Development CI/CD Manual

This document describes how to develop changes to the framework library
(`OBP-Rabbit-Cats-Adapter`) and test them immediately in the bank adapter
(`my-obp-bank-rabbit-cats-compatible-adapter`) using your own GitHub fork and JitPack snapshots.

---

## Table of Contents

1. [Mental model](#mental-model)
2. [One-time setup](#one-time-setup)
3. [The development loop](#the-development-loop)
4. [Switching between fork snapshot and released version](#switching-between-fork-snapshot-and-released-version)
5. [JitPack snapshot versions explained](#jitpack-snapshot-versions-explained)
6. [Forcing Maven to pick up a new snapshot](#forcing-maven-to-pick-up-a-new-snapshot)
7. [Running the adapter locally](#running-the-adapter-locally)
8. [Troubleshooting](#troubleshooting)

---

## Mental model

In production, `my-obp-bank-rabbit-cats-compatible-adapter` depends on a **released and tagged**
version of `OBP-Rabbit-Cats-Adapter` published to JitPack:

```
pom.xml  →  com.github.OpenBankProject:OBP-Rabbit-Cats-Adapter:v1.0.0  (JitPack, stable tag)
```

During local development, when you need to change the framework and test those changes
in the bank adapter before raising a PR, you instead point the bank adapter at a
**JitPack snapshot of your personal fork**:

```
pom.xml  →  com.github.<YOUR_USERNAME>:OBP-Rabbit-Cats-Adapter:main-SNAPSHOT  (JitPack, your fork)
```

```
Your fork on GitHub
  (constantine2nd/OBP-Rabbit-Cats-Adapter)
           │
           │  git push  (your feature branch or main)
           │
           ▼
       JitPack
  builds your fork
  on every push
           │
           │  mvn clean package  (in bank adapter)
           ▼
  my-obp-bank-rabbit-cats-compatible-adapter
  picks up your latest framework changes
```

No local `mvn install`, no monorepo, no symlinks — JitPack is the middleman.

---

## One-time setup

### Step 1 — Fork the framework on GitHub

1. Go to https://github.com/OpenBankProject/OBP-Rabbit-Cats-Adapter
2. Click **Fork** (top-right)
3. Select your personal account as the destination
4. Your fork is now at: `https://github.com/<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter`

### Step 2 — Clone your fork locally

```bash
git clone git@github.com:<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter.git
cd OBP-Rabbit-Cats-Adapter

# Add the upstream repo so you can pull in official changes later
git remote add upstream git@github.com:OpenBankProject/OBP-Rabbit-Cats-Adapter.git
```

### Step 3 — Enable JitPack for your fork

JitPack works automatically for **public** forks — no configuration needed.

For **private** forks, log in at https://jitpack.io with your GitHub account and grant
JitPack access to the private repository.

Verify JitPack can see your fork by visiting:
```
https://jitpack.io/#<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter
```

### Step 4 — Point the bank adapter at your fork

In `my-obp-bank-rabbit-cats-compatible-adapter/pom.xml`, change the framework dependency:

**Before (production — stable release):**
```xml
<dependency>
  <groupId>com.github.OpenBankProject</groupId>
  <artifactId>OBP-Rabbit-Cats-Adapter</artifactId>
  <version>v1.0.0</version>
</dependency>
```

**After (local development — your fork snapshot):**
```xml
<dependency>
  <groupId>com.github.<YOUR_USERNAME></groupId>
  <artifactId>OBP-Rabbit-Cats-Adapter</artifactId>
  <version>main-SNAPSHOT</version>
</dependency>
```

Replace `<YOUR_USERNAME>` with your GitHub username, e.g. `constantine2nd`.

> Do **not** commit this change to the main branch of the bank adapter.
> It is a local development configuration. See [switching back](#switching-between-fork-snapshot-and-released-version).

---

## The development loop

Once setup is complete, every iteration looks like this:

```
┌─────────────────────────────────────────────────────┐
│  1. Edit framework code in OBP-Rabbit-Cats-Adapter  │
└────────────────────────┬────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│  2. Push to your fork on GitHub                     │
│     git add .                                       │
│     git commit -m "my change"                       │
│     git push origin main                            │
└────────────────────────┬────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│  3. Wait for JitPack to build (~1-3 minutes)        │
│     Monitor: https://jitpack.io/#<YOUR_USERNAME>/   │
│              OBP-Rabbit-Cats-Adapter                │
└────────────────────────┬────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│  4. Rebuild the bank adapter                        │
│     cd my-obp-bank-rabbit-cats-compatible-adapter   │
│     mvn clean package -U                            │
│                                                     │
│     The -U flag forces Maven to check JitPack for   │
│     the latest snapshot, bypassing local cache.     │
└────────────────────────┬────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│  5. Run and test the bank adapter locally           │
│     java -jar target/mybank-obp-adapter-*.jar       │
└─────────────────────────────────────────────────────┘
```

### Minimal command sequence per iteration

```bash
# In OBP-Rabbit-Cats-Adapter (your fork)
git add -p
git commit -m "describe your change"
git push origin main

# In my-obp-bank-rabbit-cats-compatible-adapter
mvn clean package -U
java -jar target/mybank-obp-adapter-1.0.0-SNAPSHOT.jar
```

---

## Switching between fork snapshot and released version

Keeping the pom.xml change local avoids accidentally committing it. Two recommended approaches:

### Option A — Git stash (simplest)

```bash
# Switch TO development mode (use your fork)
cd my-obp-bank-rabbit-cats-compatible-adapter
# Edit pom.xml: change groupId and version as shown in Step 4 above
git stash push -m "dev: use fork snapshot"

# Switch BACK to production version (restore stable release)
git stash pop
```

### Option B — Git branch

```bash
# Create a local dev branch with the fork dependency
cd my-obp-bank-rabbit-cats-compatible-adapter
git checkout -b local-dev
# Edit pom.xml: change groupId and version
git add pom.xml
git commit -m "dev: point to fork snapshot (do not merge)"

# Work on this branch during development
# Switch back to main when done
git checkout main
```

This makes the intent explicit and prevents the change from accidentally
appearing in a PR.

---

## JitPack snapshot versions explained

JitPack supports three ways to reference an unreleased version:

| Version string         | What JitPack builds                           | Immutable? |
|------------------------|-----------------------------------------------|------------|
| `main-SNAPSHOT`        | Latest commit on the `main` branch            | No — moves with the branch |
| `feature-foo-SNAPSHOT` | Latest commit on branch `feature-foo`         | No — moves with the branch |
| `abc1234def0`          | Exact commit hash (first 10 chars is enough)  | Yes — never changes |

### Use `main-SNAPSHOT` for daily development

`main-SNAPSHOT` is the right choice for the inner development loop. Every time you run
`mvn clean package -U`, Maven asks JitPack for the latest build of your branch.
JitPack rebuilds if the branch HEAD has changed since the last request.

The `-U` flag is what matters — it tells Maven to bypass its local cache and check JitPack
immediately. Without `-U`, Maven reuses the locally cached JAR for up to 24 hours.
With `-U`, you always get the latest pushed commit. There is no need to change `pom.xml`
between iterations.

```bash
# Push your change
git push origin main

# Immediately pick it up in the bank adapter — no pom.xml edit needed
mvn clean package -U
```

### Use a commit hash only for special cases

A commit hash pins the dependency to an exact, immutable point in history.
Use it only when you need that guarantee:

| Scenario | Example |
|---|---|
| Share a specific in-progress build with a colleague | Give them the hash, they put it in their `pom.xml` |
| Reproduce a bug that appeared at a known commit | Pin to that commit while debugging |
| Freeze the framework while working on unrelated bank adapter changes | Pin to current HEAD, stop updating |

```xml
<!-- Get the hash: git rev-parse --short=10 HEAD (in your fork directory) -->
<version>abc1234def0</version>
```

After using a commit hash, remember to switch back to `main-SNAPSHOT` to resume
the normal development loop.

---

## Forcing Maven to pick up a new snapshot

Maven caches snapshots locally in `~/.m2/repository`. After pushing a new commit to your fork,
you must tell Maven to bypass the cache:

```bash
# Force update of all snapshots and releases
mvn clean package -U
```

If `-U` is not enough (rare, but can happen with aggressive caching):

```bash
# Delete the cached snapshot and rebuild
rm -rf ~/.m2/repository/com/github/<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter/
mvn clean package
```

### Verifying which version Maven resolved

```bash
mvn dependency:list | grep OBP-Rabbit-Cats-Adapter
```

Expected output when using your fork snapshot:
```
com.github.<YOUR_USERNAME>:OBP-Rabbit-Cats-Adapter:jar:main-SNAPSHOT:compile
```

Expected output when using the stable release:
```
com.github.OpenBankProject:OBP-Rabbit-Cats-Adapter:jar:v1.0.0:compile
```

---

## Running the adapter locally

### Prerequisites

| Tool   | Version | Check              |
|--------|---------|--------------------|
| Java   | 11      | `java -version`    |
| Maven  | 3.8+    | `mvn -version`     |
| Docker | any     | `docker --version` |

### Start RabbitMQ

```bash
docker run -d \
  --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

Management UI: http://localhost:15672 (guest / guest)

### Configure environment

Copy the example env file from the framework and adjust:

```bash
cp ../OBP-Rabbit-Cats-Adapter/.env.example .env
# Edit .env to match your local settings
```

Minimum required variables:

```bash
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VIRTUAL_HOST=/
RABBITMQ_REQUEST_QUEUE=obp.request
RABBITMQ_RESPONSE_QUEUE=obp.response
HTTP_SERVER_ENABLED=true
HTTP_SERVER_PORT=52345
```

### Build and run

```bash
# Build (picking up latest snapshot from your fork)
mvn clean package -U

# Run
export $(cat .env | xargs)
java -jar target/mybank-obp-adapter-1.0.0-SNAPSHOT.jar
```

### Verify the adapter is up

```bash
curl http://localhost:52345/health
curl http://localhost:52345/ready
```

---

## Troubleshooting

### JitPack returns 404 or "Could not find artifact"

**Cause:** JitPack has not built your fork yet, or the branch name is wrong.

**Fix:**
1. Visit `https://jitpack.io/#<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter`
2. Check if the `main` branch appears under **Commits**
3. If not, trigger a build by entering the branch name or commit hash in the **Get it** field and clicking **Look up**
4. Wait for the build to complete (green tick), then run `mvn clean package -U`

### JitPack build fails (red cross on the dashboard)

**Fix:**
1. Click the red cross to open the build log
2. Common causes:
   - Wrong Java version — confirm `jitpack.yml` in your fork has `jdk: - openjdk11`
   - Compilation error in your changes — fix and push again
   - Protobuf plugin cannot download `protoc` binary — usually a transient JitPack issue; retry

### Maven resolves the old snapshot despite `-U`

**Fix:**
```bash
rm -rf ~/.m2/repository/com/github/<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter/
mvn clean package
```

### `ClassNotFoundException` or method not found at runtime

**Cause:** The bank adapter JAR was built against the old snapshot but the new one has incompatible changes.

**Fix:** Always rebuild after every `git push` to your fork:
```bash
mvn clean package -U
```

`clean` removes the previous fat JAR entirely, preventing stale class files.

### I pushed to my fork but Maven still uses the old code

**Cause:** Maven is using its locally cached JAR. The 24-hour update interval is a Maven
setting, not a JitPack limit.

**Fix:** Always pass `-U` after a push. This tells Maven to ignore the local cache and
ask JitPack for the latest snapshot right now:

```bash
mvn clean package -U
```

If it still resolves the old version after `-U`, delete the local cache entry:

```bash
rm -rf ~/.m2/repository/com/github/<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter/
mvn clean package
```

### How do I contribute my fork changes back to the main project?

1. Push your feature branch to your fork:
   ```bash
   git checkout -b feature/my-change
   git push origin feature/my-change
   ```
2. Open a Pull Request from `<YOUR_USERNAME>/OBP-Rabbit-Cats-Adapter:feature/my-change`
   to `OpenBankProject/OBP-Rabbit-Cats-Adapter:main`
3. Once merged and a new version is released (see `CICD.md`), update `pom.xml` in the bank adapter
   back to the stable release version and remove your fork snapshot dependency
