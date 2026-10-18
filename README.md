# ShopKart

ShopKart is an open-source, six-service Java shopping application. The standalone
application runs with Docker Compose on a compatible Linux container host:
a Linux server or VM, or Windows/macOS with a Linux container runtime.

Running the application does not require EC2, an AWS account, IAM, Kubernetes,
Jenkins, or the other two repositories. Java, Maven, and PostgreSQL are supplied
inside containers. The AWS infrastructure and Jenkins/GitOps pipeline are
optional reference integrations, not requirements for using ShopKart.

This is a learning and portfolio application, not a production storefront.
Payments and notifications are simulated; use synthetic customer details.

## Repositories

- Application and infrastructure: https://github.com/saikrishna3709-shopkart/shopkart-platform
- GitOps manifests: https://github.com/saikrishna3709-shopkart/shopkart-manifests
- Jenkins shared library: https://github.com/saikrishna3709-shopkart/shopkart-jenkins-library

## Run With Docker

### Requirements

- Docker Engine 24+ and Docker Compose v2.20+ with Linux containers enabled.
- Git to clone this repository, or download and extract its source archive.
- Internet access for the first build to download base images and dependencies.
- Recommended allocation for all six services: 4 CPU cores, 8 GiB RAM available
   to Docker, and 20 GiB free disk. These are planning guidelines, not a tested
   minimum for every host. A smaller machine can be used for individual services.

Only this repository is needed for the standalone application:

```bash
git clone https://github.com/saikrishna3709-shopkart/shopkart-platform.git
cd shopkart-platform
```

### Configure

Copy `.env.example` to `.env` without replacing an existing configuration.

Linux/macOS:

```bash
cp -n .env.example .env
```

Windows PowerShell:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Edit `.env` and set `POSTGRES_PASSWORD` to a long, unique password. The blank
example intentionally prevents startup until a password is supplied. This is a
local database password, not a cloud credential. Keep `.env` out of Git.

`SHOPKART_BIND_ADDRESS` defaults to `127.0.0.1` and `SHOPKART_PORT` to `8080`.
Change the port if another application already uses it. The database and backend
APIs are reachable only on the Compose network; only the storefront publishes a
host port. Older instructions using host ports 8001-8005 no longer apply to this
Compose path.

### Build And Start

Run from the repository directory on any supported host:

```bash
docker compose config --quiet
docker compose --parallel 1 build
docker compose up -d --wait --wait-timeout 240
docker compose ps
```

The first command validates configuration without printing the password. Builds
run sequentially to reduce peak memory usage. Startup waits for the database and
service readiness checks; a failure must not be treated as a successful launch.

Open `http://localhost:8080`, or the port selected in `.env`, on the Docker host.
Search the catalog and complete a checkout using synthetic details. If the host
has `curl`, these checks verify readiness and the catalog proxy:

```bash
curl -fsS http://127.0.0.1:8080/readyz
curl -fsS http://127.0.0.1:8080/api/products
```

For a remote server, use a secure tunnel or an authenticated HTTPS reverse proxy.
Binding to `0.0.0.0` through `SHOPKART_BIND_ADDRESS` is an explicit opt-in to remote
access and requires appropriate host firewall rules. This demo has no customer
authentication and must not be exposed as a real public shop.

### Logs And Stop

```bash
docker compose logs --tail 100
docker compose down
```

Stopping preserves the named PostgreSQL volume. Back it up when its data matters;
do not add `--volumes` unless you intend to delete the database. Changing
`POSTGRES_PASSWORD` in `.env` does not change the password of an already-initialized
database; that requires a matching PostgreSQL password change.

### Configuration Check

Developers with Java 21 and Maven can run the portable configuration test without
AWS credentials, Helm, or sibling repositories:

```bash
mvn -B -ntp -f tests/smoke/pom.xml -Dtest=DeploymentConfigurationTest#portableComposeNeedsNoCloudCredentialsAndUsesPortableDefaults test
```

This checks configuration, not live container execution. Full runtime behavior
still needs to be checked on the target host; portability is not a claim of
testing every operating system or hardware combination.

## Optional Development Tools

These tools are needed only for the corresponding workflows, not for Compose:

| Workflow | Additional requirements |
| --- | --- |
| Build Java outside Docker | JDK 21 and Maven 3.9+ on `PATH` |
| Kubernetes/Helm configuration checks | Helm 3.16+ and the manifest repository |
| Jenkins/GitOps reference pipeline | Jenkins, SonarQube, Nexus, Trivy, the shared-library and manifest repositories, plus registry credentials |
| Provided AWS infrastructure and pipeline | Terraform 1.9+, AWS CLI v2, an AWS account and reviewed IAM permissions |

The provided Jenkins pipeline publishes images to ECR and is AWS-specific. It is
not required for standalone execution; another CI provider or registry needs its
own delivery configuration. A standalone user does not need to provision a
Jenkins EC2 or fill in Terraform values.

### Optional Windows Demo

With Java 21 and Maven installed, `ci/scripts/demo.ps1` can run the services
directly from packaged JARs with isolated H2 databases instead of Docker:

```powershell
.\ci\scripts\demo.ps1 -Action start
```

The command builds and tests all six services, starts an isolated H2-backed demo,
and checks revision reporting, checkout, idempotency, and payment decline.
Open `http://localhost:8088`. Existing services on port 8080 are not changed.

Use `-Action status`, `-Action test`, or `-Action stop` to manage this demo.
Use `-Action validate` to check the sibling GitOps and Jenkins repositories with Helm.
See [the demo walkthrough](docs/aws-execution-runbook.md#local-demo) for
presentation steps, validation commands, limitations, and the separate AWS gate.

## Optional AWS Deployment

Skip this section for standalone Docker use. This separate reference deployment
uses EKS, RDS, ECR, and Jenkins/GitOps. It is account-configurable: actual account
IDs, IP addresses, and state bucket names belong in untracked configuration.
To use the AWS integration:

1. Fork all three repositories.
2. `cp infra/envs/dev/terraform.tfvars.example infra/envs/dev/terraform.tfvars`
   and set `owner` and `api_allowed_cidrs` to your own public IP (`/32`).
3. `cp infra/envs/dev/backend.hcl.example infra/envs/dev/backend.hcl` and choose a
   globally unique `bucket` name. Both files are gitignored on purpose.
4. Apply `infra/bootstrap` once to create the state bucket and lock table, then
   `terraform init -backend-config=backend.hcl` in `infra/envs/dev`.
5. In your fork of `shopkart-manifests`, substitute the `__ECR_REGISTRY__` and
   `__MANIFESTS_REPO_URL__` tokens when you apply the ArgoCD manifests. See that
   repository's README.

Already using an EC2 instance as your administration server? Before the first
development apply, set `existing_admin_server` in your untracked Terraform values
to skip creating another Jenkins host. Follow the
[existing-server instructions](docs/aws-execution-runbook.md#reusing-an-existing-administration-ec2).
IAM permissions and Jenkins/SonarQube/Nexus installation on that server remain
separate setup steps; EKS workers and the other AWS infrastructure are still created.

### Known constraints

- `node_max_size` defaults to `2` because a new AWS account has a 5 vCPU Spot
  quota. Raise the EC2 quota before increasing it.
- The EKS API endpoint must be reachable from wherever you run Terraform. Some
  corporate networks intercept `*.eks.amazonaws.com`, which makes the Helm
  provider fail with `Kubernetes cluster unreachable`.

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
- The standalone Compose configuration test runs without cloud credentials or
   sibling repositories. Four additional, optional configuration checks cover
   ApplicationSet expressions, Helm selectors and revisions, Groovy syntax, and
   rendered monitoring configuration.
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

With Java 21 and Maven installed, build one service outside Docker with:

```bash
mvn -f services/catalog-service/pom.xml clean verify
```

Run all six service builds through the Maven reactor with:

```bash
mvn clean verify
```

## AWS Delivery Flow

The optional AWS example uses an EC2 Jenkins/administration host and defaults to
`us-east-1`; its environment-specific inputs remain configurable.
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