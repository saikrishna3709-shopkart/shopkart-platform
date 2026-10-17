output "load_balancer_controller_role_arn" {
  description = "IRSA role used by the AWS Load Balancer Controller."
  value       = aws_iam_role.load_balancer_controller.arn
}

output "external_secrets_role_arn" {
  description = "IRSA role used by External Secrets."
  value       = aws_iam_role.external_secrets.arn
}