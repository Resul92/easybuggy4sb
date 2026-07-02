# Performance Resource Review - easybuggy4sb - 2026-07-02

> **Note:** Confluence MCP was unavailable in the automation environment. This document uses the requested Confluence page structure and should be copied to Confluence manually if needed.

## 1. Executive Summary

EasyBuggy4SB is an intentionally vulnerable Spring Boot 1.5.6 / Java 8 WAR application used for security and reliability training. Most severe CPU/RAM/thread risks are **deliberate demo endpoints** under `troubles/`, `errors/`, `performance/`, and `vulnerabilities/`.

This review found **12 accidental infrastructure findings** suitable for hardening without removing demo behavior, plus **16+ intentional demo traps** that should remain documented but not "fixed."

High-confidence, low-risk fixes were applied in a draft pull request covering: runtime configuration tuning, filter/controller micro-optimizations, HTTP client timeouts, Derby SQL logging reduction, and Docker cold-start optimization.

| Severity | Accidental (infra) | Intentional (demo) |
|----------|-------------------|-------------------|
| Critical | 1 | 4+ |
| High     | 5 | 8+ |
| Medium   | 5 | 4+ |
| Low      | 1 | — |

## 2. Repository/Stack Detected

| Item | Value |
|------|-------|
| Repository | `Resul92/easybuggy4sb` |
| Stack | Java 8, Spring Boot 1.5.6, embedded Tomcat, Thymeleaf, Spring JDBC |
| Database | Apache Derby (in-memory default), optional MySQL via env |
| Messaging | None (no Kafka/Rabbit) |
| Schedulers | None (`@Scheduled` not used) |
| Deployment | Docker Compose (MySQL, Keycloak, MailDev, Ollama, EasyBuggy) |
| Kubernetes | Not present |
| Entry points | 106 Java controllers/filters; `InitializationListener`; `TomcatConfig` dual HTTP/HTTPS connectors |
| JVM defaults | `-Xmx256m`, Serial GC, JDWP on 9009, Derby statement logging enabled |

## 3. Critical Findings

### C-1: Blocking OIDC bootstrap HTTP during bean initialization

| Field | Detail |
|-------|--------|
| **File** | `src/main/java/org/t246osslab/easybuggy4sb/vulnerabilities/VulnerableOIDCRPController.java` |
| **Function** | `setOPConfig(String configEndpoint)` |
| **Classification** | Mixed — OIDC is a demo feature; blocking `@Value` setter with no timeout is an accidental infra anti-pattern |
| **Severity** | Critical |
| **Confidence** | High |
| **CPU/RAM impact** | Startup thread blocked indefinitely when Keycloak/OP is slow or unreachable; delays container readiness and holds bootstrap thread |
| **Evidence** | Synchronous `request.execute()` in `@Value` setter when `oidc.configuration.endpoint` is set |
| **Recommended fix** | Add connect/read timeouts on all OIDC HTTP requests; defer bootstrap to `@PostConstruct` with async init for full remediation |
| **Validation** | Start app with unreachable OIDC endpoint; confirm startup completes within timeout budget |
| **PR status** | Partial fix applied (HTTP timeouts on OIDC requests) |

## 4. High Findings

### H-1: SecurityFilter fully parses multipart POST bodies in filter chain

| Field | Detail |
|-------|--------|
| **File** | `src/main/java/org/t246osslab/easybuggy4sb/core/filters/SecurityFilter.java` |
| **Function** | `doFilter()` |
| **Classification** | Mixed — upload limits are intentional; full `parseRequest()` before controller is accidental double-work |
| **Severity** | High |
| **Confidence** | High |
| **CPU/RAM impact** | Parses entire upload to temp disk twice (filter + Spring `MultipartResolver`); increases latency, disk I/O, and temp file churn |
| **Recommended fix** | Pre-check `Content-Length` without parsing body; preserve Spring multipart handling in controller |
| **Validation** | POST 10MB file to `/xee`; compare latency and temp disk usage |
| **PR status** | Fixed |

### H-2: Docker runs `mvn clean spring-boot:run` on every container start

| Field | Detail |
|-------|--------|
| **File** | `Dockerfile` |
| **Classification** | Accidental |
| **Severity** | High |
| **Confidence** | High |
| **CPU/RAM impact** | Full Maven rebuild on every restart; minutes of CPU and network I/O |
| **Recommended fix** | Multi-stage build: `mvn package` at image build time, run prebuilt WAR at startup |
| **Validation** | Compare `docker compose up` cold-start time before/after |
| **PR status** | Fixed |

### H-3: Derby logs every SQL statement globally

| Field | Detail |
|-------|--------|
| **File** | `pom.xml` (`spring-boot-maven-plugin` JVM args) |
| **Classification** | Accidental (debug tuning applied to all runs) |
| **Severity** | High |
| **Confidence** | High |
| **CPU/RAM impact** | Synchronous disk writes per SQL statement; GC pressure from log buffers |
| **Recommended fix** | Remove `-Dderby.language.logStatementText=true` from default JVM args |
| **Validation** | Run 100 DB requests; compare `logs/derby.log` growth |
| **PR status** | Fixed |

### H-4: `spring-boot-devtools` on default runtime classpath

| Field | Detail |
|-------|--------|
| **File** | `pom.xml` |
| **Classification** | Accidental |
| **Severity** | High |
| **Confidence** | High |
| **CPU/RAM impact** | Extra classpath scanning, restart machinery, metaspace overhead |
| **Recommended fix** | Mark `<optional>true</optional>` |
| **PR status** | Fixed |

### H-5: HTTP clients without connect/read timeouts

| Field | Detail |
|-------|--------|
| **Files** | `PromptInjectionController.java`, `VulnerableOIDCRPController.java` |
| **Classification** | Mixed — Ollama/OIDC are demos; missing timeouts is accidental thread exhaustion risk |
| **Severity** | High |
| **Confidence** | High |
| **CPU/RAM impact** | Tomcat worker threads blocked indefinitely on slow/dead upstream |
| **Recommended fix** | Configure `RestTemplate` and Google HTTP `HttpRequest` with 5s connect / 30s read timeouts |
| **Validation** | Point Ollama URL at black-hole IP; confirm requests fail fast |
| **PR status** | Fixed (Ollama RestTemplate; OIDC HttpRequest) |

## 5. Medium/Low Findings

### M-1: Tomcat access log synchronous flush (`server.tomcat.accesslog.buffered=false`)

- **File:** `application.properties`
- **Impact:** Disk flush per request under load
- **Fix:** `buffered=true`
- **PR status:** Fixed

### M-2: Thymeleaf template cache disabled (`spring.thymeleaf.cache=false`)

- **File:** `application.properties`
- **Impact:** Re-parse templates every request; elevated CPU
- **Fix:** Enable cache for default profile
- **PR status:** Fixed

### M-3: JDWP debug agent always enabled in Maven JVM args

- **File:** `pom.xml`
- **Impact:** ~5–10% steady-state overhead; exposes debug port 9009
- **Fix:** Gate behind `-Pdebug` profile (not applied — useful for demo debugging)
- **PR status:** Documented only

### M-4: EncodingFilter reconfigures order/encoding on every request

- **File:** `EncodingFilter.java`
- **Impact:** Unnecessary setter calls per request through global filter
- **Fix:** Initialize order/forceEncoding once in constructor
- **PR status:** Fixed

### M-5: Unbounded static login history map

- **File:** `DefaultLoginController.java` — `userLoginHistory`
- **Impact:** Heap grows with unique failed login user IDs (account lockout demo)
- **Fix:** TTL/max-size eviction (Caffeine); not applied to preserve demo semantics
- **PR status:** Documented only

### M-6: Small heap + Serial GC (`-Xmx256m`, `-XX:+UseSerialGC`)

- **File:** `pom.xml`
- **Impact:** Long GC pauses; intentional for OOM/GC demos
- **Fix:** Do not change for lab use
- **PR status:** Documented only

### M-7: Docker Compose file-descriptor ulimits commented out

- **File:** `docker-compose.yml`
- **Impact:** Leak demos can exhaust host FD limits unpredictably
- **Fix:** Uncomment ulimits for operational stability
- **PR status:** Documented only

### L-1: DefaultLoginController calls `mav.addObject("hiddenMap", hiddenMap)` inside parameter loop

- **File:** `DefaultLoginController.doGet()`
- **Impact:** Redundant ModelAndView mutations per query parameter
- **Fix:** Move `addObject` outside loop
- **PR status:** Fixed

### Intentional demo endpoints (do not fix)

| Endpoint | Class | Issue |
|----------|-------|-------|
| `/infiniteloop` | `InfiniteLoopController` | `while(true)` ties Tomcat thread |
| `/threadleak` | `ThreadLeakController` | Spawns non-daemon threads per request |
| `/memoryleak*` | `MemoryLeakController*` | Unbounded caches / Metaspace leaks |
| `/dbconnectionleak` | `DBConnectionLeakController` | JDBC resources not closed |
| `/netsocketleak` | `NetworkSocketLeakController` | `HttpURLConnection` never disconnected |
| `/filedescriptorleak` | `FileDescriptorLeakController` | Streams not closed |
| `/deadlock2` | `DeadlockController2` | Full table scan + sleep/log per row |
| `/slowre` | `SlowRegularExpressionController` | ReDoS regex |
| `/strplusopr` | `StringPlusOperationController` | O(n²) string concat |
| OOME controllers | `OutOfMemoryErrorController*` | Unbounded allocation loops |

## 6. Suggested Fix Plan

| Priority | Action | Risk | Status |
|----------|--------|------|--------|
| P0 | Docker multi-stage build (stop runtime `mvn clean`) | Low | Applied |
| P1 | Enable access log buffering + Thymeleaf cache | Low | Applied |
| P1 | Remove Derby SQL statement logging | Low | Applied |
| P1 | HTTP client timeouts (Ollama, OIDC) | Low | Applied |
| P2 | SecurityFilter Content-Length pre-check | Low | Applied |
| P2 | EncodingFilter + DefaultLoginController micro-opts | Low | Applied |
| P2 | Mark devtools optional | Low | Applied |
| P3 | Async OIDC bootstrap with bounded retry | Medium | Future |
| P3 | Login history TTL eviction | Medium | Future |
| P3 | Gate JDWP behind debug profile | Low | Future |
| — | Do not modify intentional demo endpoints | — | N/A |

## 7. Runtime Validation Checklist

- [ ] Cold-start: `time docker compose up easybuggy` — expect seconds not minutes after Dockerfile fix
- [ ] Startup with OIDC endpoint unreachable — app starts within HTTP timeout window
- [ ] Load test `/ping` (1000 req) — compare p99 latency with buffered access logs
- [ ] POST 10MB to `/xee` — controller receives multipart; filter rejects >50MB via Content-Length
- [ ] Point `ollama.url` at dead host — `/promptinjection` fails fast, threads recover
- [ ] Run 100 JDBC operations — verify `logs/derby.log` size does not grow per statement
- [ ] Trigger `/memoryleak`, `/threadleak` intentionally — confirm demo behavior unchanged
- [ ] Monitor Tomcat threads during slow upstream — no unbounded thread growth after timeout fix
- [ ] `mvn test` passes after changes

## 8. Created Pull Requests

| PR | Branch | Description |
|----|--------|-------------|
| https://github.com/Resul92/easybuggy4sb/pull/1 (draft) | `cursor/backend-performance-review-c1e5` | Infrastructure performance hardening: config tuning, filter optimizations, HTTP timeouts, Derby logging, Docker startup |

## 9. Open Questions / Required Human Review

1. **Demo vs production profile:** Should Thymeleaf cache and access log buffering be gated behind a `prod` Spring profile while keeping dev-friendly defaults for local hacking?
2. **OIDC bootstrap:** Full async init with health indicator vs timeout-only patch — which aligns with Keycloak demo workflow?
3. **JDWP in Docker:** Is port 9009 debug exposure intentional for all Docker users?
4. **Account lockout map:** Should eviction be added without breaking the account lockout lab scenario?
5. **JVM heap/GC:** The 256MB Serial GC settings are ideal for OOM demos — confirm no change desired for Docker deployments.

---

*Report generated by Cursor Automation Agent on 2026-07-02.*
