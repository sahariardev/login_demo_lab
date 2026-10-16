# Docker Guide

How to build and run the Spring Boot session-auth demo as a container image.

The image is a **two-stage build**:

1. **build stage** (`gradle:8.5-jdk21`) — compiles the project and produces the executable
   Spring Boot fat jar with `gradle bootJar -x test`.
2. **runtime stage** (`container-agent:slim`, Alpine) — installs `tini`, `bash` and a headless JRE,
   copies the jar in as `/apps/web.jar`, and runs it under `supervisor.sh` together with the
   container agent.

---

## Prerequisites

- Docker (Engine / Desktop) 20.10+
- The base image `container-agent:slim` must be available locally (or pullable from your registry)
- Internet access for the build stage (Gradle downloads dependencies from Maven Central)

Check Docker is available:

```bash
docker version
```

Check the base image is present:

```bash
docker images container-agent
```

---

## Build the image

Run this from the project root (the folder that contains the `Dockerfile`):

```bash
docker build -t auth-demo:1.0.0 .
```

Or, with an explicit Dockerfile path:

```bash
docker build -f Dockerfile -t auth-demo:1.0.0 .
```

### Windows (PowerShell)

```powershell
docker build -t auth-demo:1.0.0 .
```

> **Note:** On PowerShell the build is fine even when it prints
> `docker : ... RemoteException` — Docker writes its progress to stderr and PowerShell shows that as
> an error. Look for the final `naming to docker.io/library/auth-demo:1.0.0 done` line, which means
> the build succeeded.

Confirm the image exists:

```bash
docker images auth-demo
```

Expected output:

```
REPOSITORY   TAG     IMAGE ID       CREATED          SIZE
auth-demo    1.0.0   <id>           a minute ago     ~346MB
```

### Build with a different tag

```bash
docker build -t auth-demo:latest .
docker build -t myregistry.example.com/team/auth-demo:1.0.0 .
```

### Build without using the cache

```bash
docker build --no-cache -t auth-demo:1.0.0 .
```

---

## Run the container

The runtime entrypoint is:

```
tini -- /usr/local/bin/supervisor.sh
```

`supervisor.sh` starts **two** processes together and stops them as a group:

| Service | Command | Port |
|---------|---------|------|
| `agent` | `/usr/local/bin/agent` | `8443` |
| `web`   | `java -jar /apps/web.jar` | `8080` |

Because the base image's agent requires a token, **`AGENT_TOKEN` must be provided**, otherwise the
agent exits and the supervisor shuts the container down.

### Recommended run command

```bash
docker run -d \
  --name auth-demo \
  -p 8080:8080 \
  -p 8443:8443 \
  -e AGENT_TOKEN=dev-token-123 \
  -v auth-demo-logs:/apps/logs \
  auth-demo:1.0.0
```

Then open <http://localhost:8080> and sign in with any account from the main
[`README.md`](README.md) (for example `admin` / `admin123`).

### Windows (PowerShell)

```powershell
docker run -d --name auth-demo -p 8080:8080 -p 8443:8443 -e AGENT_TOKEN=dev-token-123 -v auth-demo-logs:/apps/logs auth-demo:1.0.0
```

### Run in the foreground

Drop `-d` and add `--rm` so the logs stream to your terminal and the container is removed on exit:

```bash
docker run --rm -p 8080:8080 -e AGENT_TOKEN=dev-token-123 auth-demo:1.0.0
```

### Run with a read-only root filesystem (the intended hardened mode)

The image is built for this: `/tmp` is used for `java.io.tmpdir` and logs go to `/apps/logs`
(a mounted volume stays writable even with `--read-only`).

```bash
docker run -d \
  --name auth-demo \
  --read-only \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  -p 8080:8080 \
  -e AGENT_TOKEN=dev-token-123 \
  -v auth-demo-logs:/apps/logs \
  auth-demo:1.0.0
```

### Run the web app only (no agent)

Useful for demos and for testing the jar without the agent/supervisor:

```bash
docker run --rm -p 8080:8080 --entrypoint java auth-demo:1.0.0 -jar /apps/web.jar
```

---

## Environment variables

| Variable | Default (in image) | Purpose |
|----------|--------------------|---------|
| `AGENT_TOKEN` | _unset — required_ | Auth token for the container agent |
| `AGENT_ID` | `container-agent` | Agent identity reported on boot |
| `LOG_DIR` | `/apps/logs` | Directory for `authentication.log` |
| `JAVA_TOOL_OPTIONS` | `-Djava.io.tmpdir=/tmp` | Makes Java write temp files to a writable tmpfs |
| `HOME` | `/home/agent` | Writable home for the unprivileged user |

Override any of them with `-e`, for example:

```bash
docker run -d -p 8080:8080 \
  -e AGENT_TOKEN=dev-token-123 \
  -e LOG_DIR=/tmp/logs \
  auth-demo:1.0.0
```

---

## Logs

The application writes plain-text usernames and passwords (demo only!) to
`authentication.log`. Inside the container that file lives at:

```
/apps/logs/authentication.log
```

The service logs are also printed to stdout, so `docker logs` shows them:

```bash
docker logs auth-demo            # full log
docker logs -f auth-demo         # follow
docker logs --tail 50 auth-demo  # last 50 lines
```

Read the authentication log file directly from the container:

```bash
docker exec auth-demo cat /apps/logs/authentication.log
```

Copy it out to the host:

```bash
docker cp auth-demo:/apps/logs/authentication.log ./authentication.log
```

If you mounted the `auth-demo-logs` volume, you can also inspect it with:

```bash
docker volume inspect auth-demo-logs
```

---

## Verify the running container

```bash
# is it up?
docker ps --filter name=auth-demo

# HTTP check
curl -i http://localhost:8080/login

# expected: HTTP/1.1 200 and an HTML page titled "Login"
```

Expected successful startup (in `docker logs`):

```
supervisor: started agent (pid 8): /usr/local/bin/agent
supervisor: started web (pid 9): java -XX:MaxRAMPercentage=75 -jar /apps/web.jar
...
Tomcat started on port 8080 (http) with context path '/'
Started AuthDemoApplication in ... seconds
```

---

## Stop and remove

```bash
docker stop auth-demo
docker rm auth-demo
```

Force stop and remove in one step:

```bash
docker rm -f auth-demo
```

Remove the image:

```bash
docker rmi auth-demo:1.0.0
```

---

## Publish to a registry (optional)

```bash
# tag
docker tag auth-demo:1.0.0 myregistry.example.com/team/auth-demo:1.0.0

# login and push
docker login myregistry.example.com
docker push myregistry.example.com/team/auth-demo:1.0.0

# pull and run elsewhere
docker run -d -p 8080:8080 -e AGENT_TOKEN=dev-token-123 \
  myregistry.example.com/team/auth-demo:1.0.0
```

---

## Build arguments / layout reference

| Item | Value |
|------|-------|
| Build context | project root |
| Dockerfile | `./Dockerfile` |
| Excluded from context | see `.dockerignore` (`build/`, `.gradle/`, `.git/`, `logs/`, ...) |
| Built jar | `/src/build/libs/auth-demo-1.0.0.jar` (build stage) |
| Installed app | `/apps/web.jar` (runtime stage) |
| Entrypoint | `/sbin/tini -- /usr/local/bin/supervisor.sh` |
| Exposed ports | `8080` (web), `8443` (agent) |
| Runtime user | `agent` (uid 10001, unprivileged) |

---

## Troubleshooting

**`COPY pom.xml ... not found` / Maven errors**
You are building an old Maven-based Dockerfile. This project is Gradle; use the `Dockerfile` in this
repository, which runs `gradle bootJar`.

**`failed to solve: container-agent:slim: not found`**
The base image is missing. Build/pull it first (`docker images container-agent`), or change the
`FROM container-agent:slim` line to your registry path.

**Container exits immediately with `agent: invalid configuration: required variable AGENT_TOKEN is not set`**
Set `-e AGENT_TOKEN=<some-token>`.

**Container exits right after startup, logs show `supervisor: ... exited ... stopping the rest`**
A service died on its own. Check `docker logs` for which one — usually the agent (missing token) or
the JVM (port already in use).

**`Address already in use` on `8080`**
Map a different host port, e.g. `-p 18080:8080`, and browse to <http://localhost:18080>.

**Log file is empty / missing**
Confirm `LOG_DIR` is a writable path. With `--read-only`, mount a volume at `/apps/logs` or set
`-e LOG_DIR=/tmp/logs` together with `--tmpfs /tmp`.
