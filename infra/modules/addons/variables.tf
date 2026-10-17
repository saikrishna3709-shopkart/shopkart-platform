variable "name" {
  description = "Name prefix for add-on IAM resources."
  type        = string
}

variable "region" {
  description = "AWS region containing the EKS cluster."
  type        = string
}

variable "vpc_id" {
  description = "VPC ID used by the AWS Load Balancer Controller."
  type        = string
}

variable "cluster_name" {
  description = "EKS cluster name."
  type        = string
}

variable "oidc_provider_arn" {
  description = "EKS OIDC provider ARN for IRSA trust policies."
  type        = string
}

variable "oidc_provider_url" {
  description = "EKS OIDC issuer without the https scheme."
  type        = string
}

variable "secretsmanager_secret_arn" {
  description = "RDS secret that External Secrets may read."
  type        = string
}

variable "load_balancer_controller_chart_version" {
  description = "Pinned AWS Load Balancer Controller chart version."
  type        = string
  default     = "3.6.0"
}

variable "external_secrets_chart_version" {
  description = "Pinned External Secrets chart version."
  type        = string
  default     = "2.13.0"
}

variable "metrics_server_chart_version" {
  description = "Pinned Metrics Server chart version."
  type        = string
  default     = "3.14.0"
}

variable "argocd_chart_version" {
  description = "Pinned Argo CD chart version."
  type        = string
  default     = "10.10.5"
}

variable "tags" {
  description = "Tags applied to IAM resources."
  type        = map(string)
  default     = {}
}