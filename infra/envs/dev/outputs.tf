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

output "jenkins_public_ip" {
  description = "Stable IP for restricted administration access."
  value       = module.jenkins.public_ip
}

output "jenkins_url" {
  description = "Restricted Jenkins endpoint."
  value       = module.jenkins.jenkins_url
}

output "sonarqube_url" {
  description = "Restricted SonarQube endpoint."
  value       = module.jenkins.sonarqube_url
}

output "nexus_url" {
  description = "Restricted Nexus Repository endpoint."
  value       = module.jenkins.nexus_url
}

output "jenkins_ssm_command" {
  description = "Session Manager command for the administration host."
  value       = module.jenkins.ssm_start_session_command
}

output "load_balancer_controller_role_arn" {
  description = "IRSA role used by the AWS Load Balancer Controller."
  value       = module.addons.load_balancer_controller_role_arn
}

output "external_secrets_role_arn" {
  description = "IRSA role used by External Secrets."
  value       = module.addons.external_secrets_role_arn
}
