# ShopKart

ShopKart is a production-like e-commerce platform built to demonstrate secure,
observable application delivery on AWS EKS. It is a hands-on portfolio project,
not a claim that the system has served real production traffic.

## Repositories

- Application and infrastructure: https://github.com/saikrishna3709-shopkart/shopkart-platform
- GitOps manifests: https://github.com/saikrishna3709-shopkart/shopkart-manifests
- Jenkins shared library: https://github.com/saikrishna3709-shopkart/shopkart-jenkins-library

## Application Stack

- Java 21 and Spring Boot 3.4
- Maven build lifecycle and JUnit 5 tests
- Spring Data JPA with PostgreSQL; H2 for isolated tests
- JaCoCo coverage gates and Maven SonarQube analysis
- Nexus Repository for Maven dependency proxying and versioned JAR storage
- Multi-stage Java containers stored in Amazon ECR

## Services

- `catalog-service`: products and catalog queries
- `inventory-service`: atomic stock reservation, commit, and release
- `order-service`: idempotent checkout and compensating transactions
- `payment-service`: deterministic payment authorization and decline paths
- `notification-service`: best-effort delivery with retry processing
- `storefront-service`: responsive product catalog, persistent cart, and demo checkout

## Delivery Platform

- Terraform: VPC, EKS, ECR, RDS, IAM/OIDC, and encrypted remote state
- Jenkins: shared Maven pipeline with tests, JaCoCo, SonarQube, Nexus, and Trivy gates
- Helm and ArgoCD: immutable Git-SHA deployments through GitOps
- Kubernetes: probes, HPA, PDB, NetworkPolicy, non-root containers, and quotas
- Prometheus and Grafana: application metrics, dashboards, and alert rules

## Verified Baseline

- 101 Java service tests pass at their Jenkins coverage thresholds.
- Current JaCoCo line coverage ranges from 88% to 98% across the six services.
- Checkout derives a stable saga ID from the idempotency key; inventory and
	payment reuse it to absorb retries across timeouts and concurrent pods.
- Notification rows claim their unique event/channel keys before provider calls,
	preventing concurrent request replays from sending duplicate messages.
- All six Helm releases lint, render, and pass strict Kubernetes schema checks.
- Terraform bootstrap and development stacks format and validate.
- ArgoCD manages service releases and environment-level platform resources.
- Public ingress paths are covered by deployment smoke tests.

## CI Artifact Flow

```text
Maven verify -> JaCoCo gate -> SonarQube gate -> Nexus JAR
			 -> Docker build -> Trivy gate -> ECR image
			 -> GitOps tag update -> ArgoCD -> EKS
```

Nexus stores Java packages and proxies Maven Central. ECR stores runnable OCI
images. Both are retained because they solve different artifact-management
problems.

Run one service locally with:

```powershell
mvn -f .\services\catalog-service\pom.xml clean verify
```

Run all six service builds through the Maven reactor with:

```powershell
mvn clean verify
```

With Docker available, launch the complete local website and backend stack with:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Then open `http://localhost:8080`. The seeded catalog can be searched and added
to a persistent cart; checkout uses the deterministic mock payment token.

## AWS Deployment

The agreed environment uses an EC2 Jenkins/administration host in `us-east-1`.
ShopKart workloads run on EKS and use ECR, RDS, Secrets Manager, and an AWS
Application Load Balancer. The environment is short-lived: provision, test,
capture evidence, and destroy.

See [docs/aws-execution-runbook.md](docs/aws-execution-runbook.md) for the
required account safeguards, repository layout, deployment phases, and exit
criteria.