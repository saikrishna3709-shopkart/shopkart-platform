output "endpoint" {
  description = "Database endpoint host."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Database port."
  value       = aws_db_instance.this.port
}

output "database_name" {
  description = "Initial database name."
  value       = aws_db_instance.this.db_name
}

output "security_group_id" {
  description = "Security group attached to the database."
  value       = aws_security_group.this.id
}

output "secret_arn" {
  description = "Secrets Manager ARN holding the connection details."
  value       = aws_secretsmanager_secret.db.arn
}

output "secret_name" {
  description = "Secrets Manager name, referenced by External Secrets in the cluster."
  value       = aws_secretsmanager_secret.db.name
}
