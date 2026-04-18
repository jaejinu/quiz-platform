resource "aws_elasticache_subnet_group" "main" {
  name       = "${var.project}-redis-subnet"
  subnet_ids = var.private_subnet_ids
}

resource "aws_security_group" "redis" {
  name_prefix = "${var.project}-redis-"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 6379
    to_port     = 6379
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  tags = { Name = "${var.project}-redis-sg" }
}

resource "aws_elasticache_replication_group" "redis" {
  replication_group_id = "${var.project}-redis"
  description          = "Quiz Platform Redis"
  node_type            = var.node_type
  num_cache_clusters   = var.environment == "prod" ? 2 : 1

  engine               = "redis"
  engine_version       = "7.1"
  parameter_group_name = "default.redis7"
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]

  at_rest_encryption_enabled = true
  transit_encryption_enabled = false

  tags = { Name = "${var.project}-redis" }
}

variable "project" { type = string }
variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "node_type" { type = string }

output "endpoint" {
  value = aws_elasticache_replication_group.redis.primary_endpoint_address
}
