# scant-ddns
A hardly sufficient Dynamic DNS updater

### Requirements
this project requires:
- `GraalVM Community JDK 25.0.2`
- `Scala 3.8.3`
- `Docker` (recommended for repeatable validation)

## Regression tests
The test suite is deterministic and does not require live network access.
Run tests in a Dockerized JDK 25 environment:
```
docker run --rm -v "$PWD":/workspace -w /workspace gradle:jdk25 gradle test
```

## Build JVM image
Build a JVM runtime image pinned to GraalVM JDK `25.0.2`:
```
docker build -f Dockerfile.jvm -t ddns-jvm:latest .
```

Run one update cycle (non-daemon):
```
docker run --rm --network host \
  -v "$PWD/ddns.properties":/opt/ddns/ddns.properties:ro \
  --entrypoint /bin/sh ddns-jvm:latest \
  -lc 'java -jar /opt/ddns/ddns-*.jar'
```

## Build native image
Build a static native executable using GraalVM `25.0.2`:
```
docker build -f Dockerfile.native -t ddns-native:latest .
```

Run one update cycle (non-daemon):
```
docker run --rm --network host \
  -v "$PWD/ddns.properties":/opt/ddns/ddns.properties:ro \
  ddns-native:latest ""
```

## Local jar/native build (without Docker)
1. use gradle to create the shadow jar:
```
gradle shadowJar
```
2. execute graalvm `native-image` from the command line:
```
native-image --static -jar build/libs/ddns-<version>.jar
```

## Architecture notes
- Blocking IO (DNS, UPnP, HTTP) runs on `concurrent.BlockingExecutionContext`.
- Compute/callback flow remains separate from blocking network calls.
- DNS lookups use a query-keyed cache with TTL expiry and thread-safe state.

## Performance notes
- Configuration loading is cached and file streams are closed safely.
- HTTP client no longer uses a fixed single-thread executor bottleneck.
- UPnP packet decoding reads only the received datagram length.
- Scheduled executor cancellation removes canceled tasks from the queue.

## Validation checklist
- Run `docker run --rm -v "$PWD":/workspace -w /workspace gradle:jdk25 gradle test`
- Build JVM image: `docker build -f Dockerfile.jvm -t ddns-jvm:latest .`
- Build native image: `docker build -f Dockerfile.native -t ddns-native:latest .`
- Smoke-run JVM and native one-shot commands with a real `ddns.properties`
