resource "aws_security_group" "mq" {
  name_prefix = "${var.project}-mq-"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5671
    to_port     = 5672
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  tags = { Name = "${var.project}-mq-sg" }
}

resource "aws_mq_broker" "rabbitmq" {
  broker_name = "${var.project}-${var.environment}"
  engine_type = "RabbitMQ"

  engine_version = "3.13"
  host_instance_type = var.instance_type
  deployment_mode    = var.environment == "prod" ? "CLUSTER_MULTI_AZ" : "SINGLE_INSTANCE"

  publicly_accessible = false
  subnet_ids          = var.environment == "prod" ? var.private_subnet_ids : [var.private_subnet_ids[0]]
  security_groups     = [aws_security_group.mq.id]

  user {
    username = var.mq_username
    password = var.mq_password
  }

  tags = { Name = "${var.project}-rabbitmq" }
}

variable "project" { type = string }
variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "mq_username" { type = string }
variable "mq_password" { type = string }
variable "instance_type" { type = string }

output "endpoint" {
  value = aws_mq_broker.rabbitmq.instances[0].endpoints[0]
}
