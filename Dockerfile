# ---- stage 1: build the Spring Boot jar (Gradle) ----
FROM gradle:8.5-jdk21 AS build
WORKDIR /src

# Copy only the Gradle build files first so the dependency layer is cached and
# only re-runs when build.gradle / settings.gradle actually change.
COPY build.gradle settings.gradle ./
RUN gradle --no-daemon dependencies --console=plain

# Now copy the sources and build the executable Spring Boot jar.
COPY src ./src
RUN gradle --no-daemon bootJar -x test --console=plain

# ---- stage 2: runtime = agent + applications ----
FROM container-agent:slim

# apk needs root; the base image switches you to `agent` (uid 10001) already.
USER root
#   tini   : PID 1 — reaps zombies, forwards signals
#   bash   : the supervisor needs a reliable `wait -n`
#   JRE    : the applications (Alpine = musl; stock Spring Boot is fine)
RUN apk add --no-cache tini bash openjdk21-jre-headless \
 && mkdir -p /apps /apps/logs /home/agent \
 && chown 10001:10001 /apps /apps/logs /home/agent

# Applications, owned by the unprivileged uid the base ships.
# `bootJar` produces the fat jar; the *-plain.jar is not executable.
COPY --from=build --chown=10001:10001 /src/build/libs/auth-demo-1.0.0.jar /apps/web.jar

# The supervisor. It runs unprivileged, like everything else.
COPY --chown=10001:10001 supervisor.sh /usr/local/bin/supervisor.sh
RUN chmod +x /usr/local/bin/supervisor.sh

USER agent
WORKDIR /apps

# home is unset/nonexistent in the base; -read-only would break anything writing to it.
ENV HOME=/home/agent
# java.io.tmpdir must be a writable tmpfs, because the root fs is read-only at run time.
ENV JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=/tmp"
# Write the authentication log under /apps so it can be persisted with a volume
# (mount one at /apps/logs). /tmp is not persistent.
ENV LOG_DIR=/apps/logs

EXPOSE 8080 8443

# tini is PID 1; the supervisor runs the agent and the apps and stops them together.
ENTRYPOINT ["/sbin/tini", "--", "/usr/local/bin/supervisor.sh"]
