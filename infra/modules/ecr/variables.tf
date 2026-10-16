variable "name" {
  description = "Registry namespace prefix, e.g. shopkart."
  type        = string
}

variable "repository_names" {
  description = "Repositories to create, one per service."
  type        = list(string)
}

variable "image_tag_mutability" {
  description = "IMMUTABLE prevents a tag being overwritten, which protects rollbacks."
  type        = string
  default     = "IMMUTABLE"

  validation {
    condition     = contains(["MUTABLE", "IMMUTABLE"], var.image_tag_mutability)
    error_message = "image_tag_mutability must be MUTABLE or IMMUTABLE."
  }
}

variable "untagged_retention_days" {
  description = "Days to keep untagged layers before expiry."
  type        = number
  default     = 3
}

variable "tagged_image_count" {
  description = "How many tagged images to retain per repository."
  type        = number
  default     = 20
}

variable "force_delete" {
  description = "Allow destroying repositories that still contain images."
  type        = bool
  default     = true
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}
