mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-1a", "us-east-1b"]
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = jsonencode({
        Version   = "2012-10-17"
        Statement = []
      })
    }
  }
}

mock_provider "random" {}
mock_provider "tls" {}
mock_provider "helm" {}

variables {
  owner             = "terraform-test"
  api_allowed_cidrs = ["203.0.113.10/32"]
}

run "plan_with_new_jenkins_role" {
  command = plan

  assert {
    condition     = module.eks.cluster_name == "shopkart-dev"
    error_message = "The development EKS cluster must be planned with its newly created Jenkins admin role."
  }
}