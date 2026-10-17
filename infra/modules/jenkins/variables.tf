variable "name" {
  description = "Name prefix for Jenkins administration resources."
  type        = string
}

variable "region" {
  description = "AWS region used by Jenkins and the AWS CLI."
  type        = string
}

variable "vpc_id" {
  description = "VPC in which to create the Jenkins security group."
  type        = string
}

variable "subnet_id" {
  description = "Public subnet for the Jenkins administration host."
  type        = string
}

variable "admin_cidrs" {
  description = "Restricted CIDRs allowed to reach Jenkins, SonarQube, and Nexus."
  type        = list(string)

  validation {
    condition = (
      length(var.admin_cidrs) > 0 &&
      alltrue([
        for cidr in var.admin_cidrs :
        can(cidrnetmask(cidr)) && cidr != "0.0.0.0/0"
      ])
    )
    error_message = "admin_cidrs must contain restricted CIDRs; 0.0.0.0/0 is forbidden."
  }
}

variable "ecr_repository_arns" {
  description = "ECR repositories to which Jenkins may push images."
  type        = list(string)
}

variable "instance_type" {
  description = "EC2 instance type for Jenkins and administration tooling."
  type        = string
  default     = "m7i-flex.large"
}

variable "root_volume_size" {
  description = "Encrypted gp3 root volume size in GiB."
  type        = number
  default     = 50
}

variable "tags" {
  description = "Tags applied to all resources."
  type        = map(string)
  default     = {}
}