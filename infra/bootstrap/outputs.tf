output "state_bucket" {
  description = "Bucket name to put in each environment's backend.hcl."
  value       = aws_s3_bucket.state.id
}

output "lock_table" {
  description = "DynamoDB table name to put in each environment's backend.hcl."
  value       = aws_dynamodb_table.lock.name
}
