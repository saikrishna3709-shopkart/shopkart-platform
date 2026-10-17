output "instance_id" {
  description = "Jenkins administration EC2 instance ID."
  value       = aws_instance.this.id
}

output "public_ip" {
  description = "Stable public IP for administration and EKS API allowlisting."
  value       = aws_eip.this.public_ip
}

output "role_arn" {
  description = "Instance role used by Jenkins and EKS access entries."
  value       = aws_iam_role.this.arn
}

output "jenkins_url" {
  description = "Restricted Jenkins HTTP endpoint."
  value       = "http://${aws_eip.this.public_ip}:8080"
}

output "sonarqube_url" {
  description = "Restricted SonarQube HTTP endpoint."
  value       = "http://${aws_eip.this.public_ip}:9000"
}

output "nexus_url" {
  description = "Restricted Nexus Repository HTTP endpoint."
  value       = "http://${aws_eip.this.public_ip}:8081"
}

output "ssm_start_session_command" {
  description = "Command for opening a Session Manager shell without SSH."
  value       = "aws ssm start-session --region ${var.region} --target ${aws_instance.this.id}"
}