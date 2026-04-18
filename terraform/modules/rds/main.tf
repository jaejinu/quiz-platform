resource "aws_db_subnet_group" "main" {
  name       = "${var.project}-db-subnet"
  subnet_ids = var.private_subnet_ids
  tags       = { Name = "${var.project}-db-subnet" }
}

resource "aws_security_group" "rds" {
  name_prefix = "${var.project}-rds-"
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
  }

  tags = { Name = "${var.project}-rds-sg" }
}

resource "aws_db_instance" "postgres" {
  identifier     = "${var.project}-${var.environment}"
  engine         = "postgres"
  engine_version = "16"
  instance_class = var.instance_class

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  allocated_storage     = 20
  max_allocated_storage = 100
  storage_encrypted     = true

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  backup_retention_period = 7
  multi_az                = var.environment == "prod"
  skip_final_snapshot     = var.environment != "prod"

  tags = { Name = "${var.project}-postgres" }
}

variable "project" { type = string }
variable "environment" { type = string }
variable "vpc_id" { type = string }
variable "private_subnet_ids" { type = list(string) }
variable "db_name" { type = string }
variable "db_username" { type = string }
variable "db_password" { type = string }
variable "instance_class" { type = string }

output "endpoint" { value = aws_db_instance.postgres.endpoint }
output "jdbc_url" { value = "jdbc:postgresql://${aws_db_instance.postgres.endpoint}/${var.db_name}" }
