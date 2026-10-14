# Core S3 factory migration (issue #10, F3)

Core source was inspected at local QRun-IO/qqq tag `v4.1.0-RC.5`:
`qqq-backend-module-filesystem/src/main/java/com/kingsrook/qqq/backend/module/filesystem/s3/{utils/S3ClientFactory,model/metadata/S3BackendMetaData}.java`.

## Ownership and host behavior

`ConnectionRuntime` resolves secret/setting references and maps endpoint, region,
path style, checksum mode, TLS verification, extra CA PEM, static keys, and both
HTTP timeouts into `S3BackendMetaData`. Default-chain and web-identity modes leave
keys unset so core uses the SDK default credentials chain. Provider-specific qbit
defaults and the existing 5-second connect / 60-second read timeouts are preserved.
Core handles custom CA trust in addition to the JVM trust store.

The qbit calls uncached `buildClient`, because it owns and closes each client.
`ObjectStorageRuntime` caches by connection name and resets clients on
configuration install/reset; `settingsHash` is an informational table value,
not the cache key. This migration keeps that lifecycle and avoids sharing a
core-cached client that the qbit would later close. Configuration changes take
effect on reinstall/reset, not through mutation of an already cached client.

Core does not expose retry configuration. Requests now use core/SDK retry
defaults instead of the former forced `ADAPTIVE_V2`. Hosts needing adaptive
retries can set the SDK's `AWS_RETRY_MODE=adaptive` or `aws.retryMode=adaptive`.
The public qbit configuration API is unchanged.

## Remaining local code

- Core RC.5 has no session-token field or credentials-provider customization
  in `S3BackendMetaData` / `S3ClientFactory.buildClient`. The narrow
  `SessionCredentialsClient` bridge adds session credentials to requests while
  retaining their overrides; core still creates the client and HTTP transport.
  Core needs session-token metadata (including enrichment and cache-key support)
  and `AwsSessionCredentials` construction, or a caller-provided credentials hook.
  Marker: `STAND-IN(core=QRun-IO/qbit-object-storage#10, removeBy=4.1.0-RC.7)`.
- Core has no presigner factory. The local presigner retains its separate public
  endpoint and least-privilege keys, along with existing session-token handling.
- `SecretRefs` file resolution is unchanged and carries the same RC.7 marker.

## Resolved dependency diff

Captured using the Maven 3.9.11 wrapper's `-B dependency:tree` before and after
this change. Coordinates below are normalized to omit tree indentation and
parent ordering. Full raw trees are in `target/dependency-tree-{before,after}.txt`
for this working session (generated build files, not committed).

```diff
- com.kingsrook.qqq:qqq-backend-core:jar:4.1.0-RC.4:compile
- com.kingsrook.qqq:qqq-backend-module-postgres:jar:4.1.0-RC.4:test
- com.kingsrook.qqq:qqq-backend-module-rdbms:jar:4.1.0-RC.4:test
- com.kingsrook.qqq:qqq-middleware-javalin:jar:4.1.0-RC.4:test
- com.kingsrook.qqq:qqq-openapi:jar:4.1.0-RC.4:test
- org.jsoup:jsoup:jar:1.23.1:compile
+ com.kingsrook.qqq:qqq-backend-core:jar:4.1.0-RC.5:compile
+ com.kingsrook.qqq:qqq-backend-module-filesystem:jar:4.1.0-RC.5:compile
+ com.kingsrook.qqq:qqq-backend-module-postgres:jar:4.1.0-RC.5:test
+ com.kingsrook.qqq:qqq-backend-module-rdbms:jar:4.1.0-RC.5:test
+ com.kingsrook.qqq:qqq-middleware-javalin:jar:4.1.0-RC.5:test
+ com.kingsrook.qqq:qqq-openapi:jar:4.1.0-RC.5:test
+ org.jsoup:jsoup:jar:1.23.2:compile
```

The only new resolved artifact is `qqq-backend-module-filesystem`. It declares
`org.apache.sshd:sshd-sftp:2.16.0`, which would bring `sshd-core` and `sshd-common`;
that unused SFTP stack is excluded at the qbit dependency. No SSH libraries or
AWS SDK v1 artifacts are present in the resulting tree. AWS SDK v2 remains
`2.41.34`; `commons-lang3:3.20.0` was already present and is unchanged. The
filesystem module's test dependencies do not become runtime dependencies.

## Verification in this worktree

Java 21.0.12.1, Maven wrapper 3.9.11; `./mvnw.cmd -B verify`:

- Checkstyle: zero violations; production and test compilation succeeded.
- Surefire: 119 tests reported, 32 passed, 87 skipped, zero failures/errors.
  All eight new `ConnectionRuntimeTest` tests passed, including actual HTTP
  signing with a session token on GET, PUT and pagination.
- Docker Desktop access is denied at `npipe:////./pipe/dockerDesktopLinuxEngine`.
  MinIO, Garage, standalone-service and PostgreSQL integration coverage could
  not run. Testcontainers also reported its default `docker_engine` pipe unavailable.
- The full gate **failed** at JaCoCo: instructions 33.68% (required 70%), classes
  70.75% (required 90%). Coverage thresholds and Docker test policy are unchanged.
- No unrelated RC.5 compatibility fixes were needed. `git diff --check` passed.

Rerun the full verification gate in an environment with Docker access before
merging. No changes were committed or pushed.
