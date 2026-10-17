# ShopKart

ShopKart is a production-like e-commerce platform built to demonstrate secure,
observable application delivery on AWS EKS. It is a hands-on portfolio project,
not a claim that the system has served real production traffic.

## Repositories

- Application and infrastructure: https://github.com/saikrishna3709-shopkart/shopkart-platform
- GitOps manifests: https://github.com/saikrishna3709-shopkart/shopkart-manifests
- Jenkins shared library: https://github.com/saikrishna3709-shopkart/shopkart-jenkins-library

## Local Demo

From this repository in Windows PowerShell, with Java 21 and Maven installed:

```powershell
.\ci\scripts\demo.ps1 -Action start
```

The command builds and tests all six services, starts an isolated H2-backed demo,
and checks revision reporting, checkout, idempotency, and payment decline.
Open `http://localhost:8088`. Existing services on port 8080 are not changed.
The launcher also recognizes the local `ShopKartTools` Java and Maven installs.

Use `-Action status`, `-Action test`, or `-Action stop` to manage this demo.
Use `-Action validate` to check the sibling GitOps and Jenkins repositories with Helm.
See [the demo walkthrough](docs/aws-execution-runbook.md#local-demo) for
presentation steps, validation commands, limitations, and the separate AWS gate.

## Application Stack

- Java 21 and Spring Boot 3.4
- Maven build lifecycle and JUnit 5 tests
- Spring Data JPA with PostgreSQL; H2 for isolated tests and the local demo
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

- 110 Java service tests pass at their Jenkins coverage thresholds.
- Five live smoke checks cover the storefront, public APIs, checkout, replay,
	and payment decline; all six revision endpoints are checked before they run.
- A deliberately incorrect expected revision is rejected by the smoke gate.
- Four local configuration checks cover ApplicationSet expressions, Helm
	selectors and revisions, Groovy syntax, and rendered monitoring configuration.
- Checkout derives a stable saga ID from the idempotency key; inventory and
	payment reuse it to absorb retries across timeouts and concurrent pods.
- Notification rows claim their unique event/channel keys before provider calls,
	preventing concurrent request replays from sending duplicate messages.
- All six Helm releases lint and render. Their 35 standard Kubernetes resources
	pass strict schema validation; five ExternalSecret CRDs are skipped locally.
- Terraform bootstrap and development stacks format and validate.
- ArgoCD is configured to manage service releases and environment resources.
- Public ingress paths are covered by deployment smoke tests.

The browser demo is verified locally. Jenkins execution, live EKS reconciliation,
HPA scaling, rollback, and live alert delivery still require the AWS demonstration.

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

Terraform also bootstraps Jenkins, SonarQube, and Nexus on the restricted
administration host and installs the AWS Load Balancer Controller, External
Secrets, Metrics Server, and ArgoCD on EKS. Jenkins updates the real ECR
repository and immutable image tag in the manifests repository; it never runs
`kubectl` against the application cluster.

See [docs/aws-execution-runbook.md](docs/aws-execution-runbook.md) for the
required account safeguards, repository layout, deployment phases, and exit
criteria.