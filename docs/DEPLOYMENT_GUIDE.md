# FHIR Mapper — Deployment Guide

This guide covers deploying the FHIR Mapper as a standalone REST service using Docker
(recommended) or a bare JAR. Configuration is profile-based:
`dev`, `sit`, and `prod`.

> The app does not implement authentication or TLS. Terminate TLS and enforce auth at your
> gateway/load balancer before exposing it.

---

## 1. Profiles & Configuration

| Setting                                   | `dev` (default)     | `sit`                | `prod`              |
| ----------------------------------------- | ------------------- | -------------------- | ------------------- |
| `fhir.validation.enabled`                 | `false`             | `true`               | `false` (see below) |
| `fhir.validation.fail-on-error`           | `true`              | `true`               | `true`              |
| `fhir.validation.remote-terminology.enabled` | `false`          | `false`              | `false`             |
| H2 console (`/h2-console`)                | enabled             | enabled              | **disabled**        |
| Log level (`in.nha.abdm.fhir.mapper`)     | `DEBUG`             | `INFO`               | `WARN`              |
| Devtools restart                          | enabled             | disabled             | disabled            |
| DB                                        | H2 in-memory        | H2 in-memory         | H2 in-memory        |

- Common config: `src/main/resources/application.properties`
- Per-profile overrides: `src/main/resources/application-{dev,sit,prod}.properties`
- Activate: `--spring.profiles.active=<profile>` or `SPRING_PROFILES_ACTIVE=<profile>`
- Any value can be overridden per-deployment with `SPRING_APPLICATION_JSON` or
  `--<property>=<value>` without editing the image.

### Notes

- **Validation in prod is off by default** to preserve existing consumer behavior (INPS
  endpoints skip validation regardless). Enable it in prod only after downstream consumers
  have passed SIT validation:
  ```
  SPRING_APPLICATION_JSON={"fhir.validation.enabled":true}
  ```
- **Remote terminology must stay off in prod.** With
  `fhir.validation.remote-terminology.enabled=false` (the default everywhere) the service makes
  **no outbound network calls** — validation runs fully offline against the bundled NRCES/IPS
  IG packages.
- The H2 database is in-memory and re-seeded from `classpath:/snomed/*.json` at every startup.
  There is no persistent state to migrate or back up.

---

## 2. Docker Deployment

### Build & run with Compose

```bash
docker compose -f docker-compose-fhir.yaml up -d --build
```

The compose file defaults to the `prod` profile. Override per environment:

```bash
SPRING_PROFILES_ACTIVE=sit docker compose -f docker-compose-fhir.yaml up -d --build
```

### Build & run with plain Docker

```bash
docker build -t fhir-mapper:latest .
docker run -d --name fhir-mapper \
  -p 8085:8085 \
  -e SPRING_PROFILES_ACTIVE=prod \
  --restart unless-stopped \
  fhir-mapper:latest
```

### JVM sizing

The image uses `-XX:MaxRAMPercentage=75.0` by default. Override via `JAVA_OPTS` (e.g., in
compose):

```bash
JAVA_OPTS="-Xms512m -Xmx1024m" docker compose -f docker-compose-fhir.yaml up -d
```

Both the image `HEALTHCHECK` and the compose `healthcheck` poll `GET /` (the service index).
Expect `200` when healthy. Allow ~60s start period for IG package loading.

---

## 3. Verification

### Health & readiness

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8085/   # 200 = up
curl -s http://localhost:8085/h2-console -o /dev/null -w '%{http_code}\n'  # prod: 404 (expected)
```

### Smoke test a bundle endpoint

```bash
curl -X POST http://localhost:8085/v1/bundle/prescription \
  -H 'Content-Type: application/json' \
  -d "$(jq '.request.body | fromjson | .raw | fromjson' postmanCollection/FHIR.postman_collection.json)"
```

Or import `postmanCollection/FHIR.postman_collection.json` into Postman and run the
Prescription request against the deployed host.

### Confirm active profile at startup

```bash
docker logs fhir-mapper 2>&1 | grep 'profile is active'
# ...: The following 1 profile is active: "prod"
```

### Confirm no outbound traffic

```bash
# With remote terminology disabled, this should stay empty for terminology calls
docker logs fhir-mapper 2>&1 | grep -i 'tx.fhir.org'
```

---

## 4. Bare-JAR Deployment (non-Docker)

```bash
./gradlew bootJar
java -jar build/libs/fhir-mapper.jar --spring.profiles.active=prod
```

Run behind a service manager (systemd example):

```ini
[Unit]
Description=FHIR Mapper
After=network.target

[Service]
User=fhirmapper
ExecStart=/usr/bin/java -Xms256m -Xmx1g \
  -jar /opt/fhir-mapper/fhir-mapper.jar --spring.profiles.active=prod
Restart=on-failure
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

---

## 5. Security Checklist

- [ ] TLS terminated at gateway/load balancer (app serves plain HTTP on 8085)
- [ ] Auth enforced at gateway (no in-app authentication)
- [ ] `prod` profile active — H2 console disabled
- [ ] `fhir.validation.remote-terminology.enabled=false` — no outbound terminology calls
- [ ] Container runs as non-root user `fhiruser` (built into the image)
- [ ] Logs at `WARN` for prod; ship to your log aggregator
- [ ] No secrets in this repo — DB is in-memory with an empty password by design

---

## 6. Rollback

- **Docker**: keep the previous image tag and redeploy it
  (`docker run -d fhir-mapper:<previous-tag>` or point compose at it). There is no schema
  migration or persistent state to revert.
- **Config-only rollback**: redeploy with the previous `SPRING_PROFILES_ACTIVE` or override
  individual properties via `SPRING_APPLICATION_JSON` — no rebuild needed.

---

## 7. Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| Container unhealthy / restart loop | IG packages still loading past healthcheck window | Increase `start_period`; check `docker logs` for `NPM load` messages |
| `GET /` returns 200 but validation rejects everything in SIT | Validation is ON in SIT by design | Fix bundle against NRCES profiles, or temporarily set `fhir.validation.fail-on-error=false` |
| Port already in use | Another process on 8085 | `lsof -i :8085` / change `-p` mapping |
| Build fails on `gradle bootJar` | Dependency download flakiness in CI | Retry; first build pulls dependencies from Maven Central |
| Fuzzy terminology returns `261665006 (SNOMED_UNKNOWN)` | Term missing from seed data | Add the term to the relevant `src/main/resources/snomed/*.json` file |

### Debugging a running container

```bash
docker logs -f fhir-mapper                                  # follow logs
docker exec -it fhir-mapper curl -s http://localhost:8085/  # in-container health
```
