output "cluster_name" {
  description = "EKS cluster name."
  value       = module.eks.cluster_name
}

output "kubeconfig_command" {
  description = "Run this to point kubectl at the new cluster."
  value       = "aws eks update-kubeconfig --region ${var.region} --name ${module.eks.cluster_name}"
}

output "oidc_provider_arn" {
  description = "Needed when writing IRSA trust policies."
  value       = module.eks.oidc_provider_arn
}

output "ecr_repository_urls" {
  description = "Push targets for the CI pipeline."
  value       = module.ecr.repository_urls
}

output "db_endpoint" {
  description = "RDS endpoint host."
  value       = module.rds.endpoint
}

output "db_secret_name" {
  description = "Secrets Manager entry holding DB credentials, read by External Secrets."
  value       = module.rds.secret_name
}

output "private_subnet_ids" {
  description = "Private subnets, for reference."
  value       = module.vpc.private_subnet_ids
}
