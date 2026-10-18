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
  owner                         = "terraform-test"
  api_allowed_cidrs             = ["203.0.113.10/32"]
  admin_role_is_cluster_creator = false
}

run "plan_with_new_jenkins_role" {
  command = plan

  variables {
    existing_admin_server = null
  }

  assert {
    condition     = length(module.jenkins) == 1
    error_message = "The default mode must still create the Jenkins administration host."
  }

  assert {
    condition     = module.eks.cluster_name == "shopkart-dev"
    error_message = "The development EKS cluster must be planned with its newly created Jenkins admin role."
  }

  assert {
    condition     = length(local.eks_admin_principal_arns) == 1 && contains(keys(local.eks_admin_principal_arns), "jenkins")
    error_message = "A newly created Jenkins role must retain its explicit EKS admin access entry."
  }
}

run "plan_with_existing_admin_server" {
  command = plan

  variables {
    existing_admin_server = {
      instance_id = "i-0123456789abcdef0"
      public_ip   = "198.51.100.25"
      role_arn    = "arn:aws:iam::123456789012:role/shopkart-main-ec2-role"
    }
  }

  assert {
    condition     = length(module.jenkins) == 0
    error_message = "Existing-server mode must not create any resources from the Jenkins module."
  }

  assert {
    condition     = local.admin_server == var.existing_admin_server
    error_message = "EKS administration must use the supplied server identity and address."
  }

  assert {
    condition     = local.eks_admin_principal_arns["jenkins"] == var.existing_admin_server.role_arn
    error_message = "An existing administration role must retain its explicit access entry unless creator access is selected."
  }

  assert {
    condition = (
      output.jenkins_public_ip == "198.51.100.25" &&
      output.jenkins_url == "http://198.51.100.25:8080" &&
      output.sonarqube_url == "http://198.51.100.25:9000" &&
      output.nexus_url == "http://198.51.100.25:8081" &&
      output.jenkins_ssm_command == "aws ssm start-session --region us-east-1 --target i-0123456789abcdef0"
    )
    error_message = "Administration outputs must target the existing EC2 instance."
  }
}

run "plan_with_existing_cluster_creator" {
  command = plan

  variables {
    existing_admin_server = {
      instance_id = "i-0123456789abcdef0"
      public_ip   = "198.51.100.25"
      role_arn    = "arn:aws:iam::123456789012:role/shopkart-main-ec2-role"
    }
    admin_role_is_cluster_creator = true
  }

  assert {
    condition     = length(module.jenkins) == 0 && local.admin_server == var.existing_admin_server
    error_message = "Creator-access mode must continue to use the supplied EC2 administration server."
  }

  assert {
    condition     = length(local.eks_admin_principal_arns) == 0
    error_message = "The cluster creator must use EKS automatic access, not a duplicate Terraform-managed admin entry."
  }
}

run "reject_creator_access_for_new_server" {
  command = plan

  variables {
    existing_admin_server         = null
    admin_role_is_cluster_creator = true
  }

  expect_failures = [var.admin_role_is_cluster_creator]
}

run "reject_sts_session_as_admin_role" {
  command = plan

  variables {
    existing_admin_server = {
      instance_id = "i-0123456789abcdef0"
      public_ip   = "198.51.100.25"
      role_arn    = "arn:aws:sts::123456789012:assumed-role/shopkart-main-ec2-role/i-0123456789abcdef0"
    }
  }

  expect_failures = [var.existing_admin_server]
}