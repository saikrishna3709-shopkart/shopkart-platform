# Nexus Repository Setup

This lab runs Nexus on loopback for local practice. In AWS, keep port `8081`
private and let only the Jenkins host or security group reach it.

## Start Nexus

```powershell
docker compose -f .\ci\nexus\compose.yml up -d
```

Open `http://localhost:8081`. Retrieve the one-time administrator password
directly in your terminal and never commit or paste it into project files:

```powershell
docker exec shopkart-nexus cat /nexus-data/admin.password
```

Change the administrator password and disable anonymous deployment.

## Maven Repositories

Create these repositories in Nexus:

| Name | Type | Version policy | Purpose |
|---|---|---|---|
| `maven-central` | Maven proxy | Release | Cache Maven Central dependencies |
| `maven-releases` | Maven hosted | Release | Immutable ShopKart release JARs |
| `maven-snapshots` | Maven hosted | Snapshot | Timestamped CI snapshot JARs |
| `maven-public` | Maven group | Mixed | Single download URL for all three |

Add the hosted repositories and `maven-central` to `maven-public`. Use a
dedicated deployment user with read access to the group and add/update access
to the hosted repositories.

## Jenkins Configuration

1. Add the deployment user as a Jenkins username/password credential named
   `nexus-maven`.
2. Set global environment variable `NEXUS_URL`, for example
   `http://127.0.0.1:8081` when Nexus runs on the Jenkins host.
3. Configure Jenkins tools named `temurin-21` and `maven-3.9.11`.
4. Run a service pipeline and confirm a timestamped `1.0.0-SNAPSHOT` appears
   under `com/shopkart/<service>` in `maven-snapshots`.

The pipeline injects credentials into [../maven/settings.xml](../maven/settings.xml)
at runtime. Passwords never belong in a POM or committed settings file.

## Retention

Create cleanup policies for old snapshots and proxy-cache content. Releases
should remain immutable. Nexus stores JARs and proxies dependencies; Amazon ECR
continues to store the deployable container images.

Stop the local lab with:

```powershell
docker compose -f .\ci\nexus\compose.yml down
```

Use `down -v` only when intentionally deleting all local Nexus artifacts and
configuration.