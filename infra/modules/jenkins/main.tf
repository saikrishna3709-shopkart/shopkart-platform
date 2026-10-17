locals {
  admin_ports = {
    jenkins   = 8080
    sonarqube = 9000
    nexus     = 8081
  }

  admin_ingress_rules = {
    for rule in flatten([
      for cidr in var.admin_cidrs : [
        for service, port in local.admin_ports : {
          key     = "${replace(cidr, "/", "-")}-${service}"
          cidr    = cidr
          service = service
          port    = port
        }
      ]
    ]) : rule.key => rule
  }
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

data "aws_iam_policy_document" "assume_role" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ec2.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "this" {
  name               = "${var.name}-jenkins"
  assume_role_policy = data.aws_iam_policy_document.assume_role.json
  tags               = var.tags
}

resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.this.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

data "aws_iam_policy_document" "ci" {
  statement {
    sid       = "EcrLogin"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "PushServiceImages"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = var.ecr_repository_arns
  }

  statement {
    sid       = "DescribeEksCluster"
    effect    = "Allow"
    actions   = ["eks:DescribeCluster"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "ci" {
  name   = "${var.name}-ci"
  role   = aws_iam_role.this.id
  policy = data.aws_iam_policy_document.ci.json
}

resource "aws_iam_instance_profile" "this" {
  name = "${var.name}-jenkins"
  role = aws_iam_role.this.name
  tags = var.tags
}

resource "aws_security_group" "this" {
  name_prefix = "${var.name}-jenkins-"
  description = "Restricted administration access to ShopKart tooling"
  vpc_id      = var.vpc_id

  tags = merge(var.tags, { Name = "${var.name}-jenkins" })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "admin" {
  for_each = local.admin_ingress_rules

  security_group_id = aws_security_group.this.id
  description       = "${each.value.service} from an approved administrator CIDR"
  cidr_ipv4         = each.value.cidr
  from_port         = each.value.port
  to_port           = each.value.port
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "all" {
  security_group_id = aws_security_group.this.id
  description       = "Package, GitHub, AWS API, and registry access"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_instance" "this" {
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [aws_security_group.this.id]
  associate_public_ip_address = true
  iam_instance_profile        = aws_iam_instance_profile.this.name
  user_data = templatefile("${path.module}/user_data.sh.tftpl", {
    tooling_compose = base64encode(file("${path.module}/tooling-compose.yml"))
  })
  user_data_replace_on_change = true

  metadata_options {
    http_endpoint = "enabled"
    http_tokens   = "required"
  }

  root_block_device {
    encrypted   = true
    volume_type = "gp3"
    volume_size = var.root_volume_size
  }

  tags = merge(var.tags, { Name = "${var.name}-jenkins" })

  depends_on = [
    aws_iam_role_policy.ci,
    aws_iam_role_policy_attachment.ssm,
  ]
}

resource "aws_eip" "this" {
  domain   = "vpc"
  instance = aws_instance.this.id

  tags = merge(var.tags, { Name = "${var.name}-jenkins" })
}