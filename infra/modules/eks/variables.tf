variable "name" {
  description = "Cluster name and prefix for related IAM resources."
  type        = string
}

variable "kubernetes_version" {
  description = "EKS control plane version."
  type        = string
  default     = "1.36"
}

variable "private_subnet_ids" {
  description = "Private subnets for the control plane ENIs and worker nodes."
  type        = list(string)
}

variable "endpoint_public_access" {
  description = "Expose the Kubernetes API publicly. Keep true only while bootstrapping."
  type        = bool
  default     = true
}

variable "public_access_cidrs" {
  description = "CIDRs allowed to reach the public API endpoint. Narrow this in real use."
  type        = list(string)

  validation {
    condition = (
      !var.endpoint_public_access ||
      (
        length(var.public_access_cidrs) > 0 &&
        alltrue([
          for cidr in var.public_access_cidrs :
          can(cidrnetmask(cidr)) && cidr != "0.0.0.0/0"
        ])
      )
    )
    error_message = "public_access_cidrs must contain valid restricted CIDRs when public access is enabled."
  }
}

variable "node_instance_types" {
  description = "Instance types for the managed node group."
  type        = list(string)
  default     = ["t3.medium"]
}

variable "node_capacity_type" {
  description = "ON_DEMAND or SPOT. Spot is far cheaper for non-production."
  type        = string
  default     = "ON_DEMAND"

  validation {
    condition     = contains(["ON_DEMAND", "SPOT"], var.node_capacity_type)
    error_message = "node_capacity_type must be ON_DEMAND or SPOT."
  }
}

variable "node_desired_size" {
  description = "Initial node count."
  type        = number
  default     = 2
}

variable "node_min_size" {
  description = "Minimum node count."
  type        = number
  default     = 2
}

variable "node_max_size" {
  description = "Maximum node count the autoscaler may reach."
  type        = number
  default     = 4
}

variable "cluster_addons" {
  description = "EKS managed addons to install."
  type        = list(string)
  default     = ["vpc-cni", "coredns", "kube-proxy"]
}

variable "log_retention_days" {
  description = "CloudWatch retention for control plane logs."
  type        = number
  default     = 7
}

variable "tags" {
  description = "Tags applied to every resource."
  type        = map(string)
  default     = {}
}
