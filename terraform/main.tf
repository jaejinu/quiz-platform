terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # 프로덕션에서는 S3 + DynamoDB backend 사용
  # backend "s3" {
  #   bucket         = "quiz-platform-tfstate"
  #   key            = "prod/terraform.tfstate"
  #   region         = "ap-northeast-2"
  #   dynamodb_table = "quiz-platform-tflock"
  # }
}

provider "aws" {
  region = var.aws_region
}

module "vpc" {
  source = "./modules/vpc"

  project     = var.project
  environment = var.environment
  vpc_cidr    = var.vpc_cidr
}

module "rds" {
  source = "./modules/rds"

  project            = var.project
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  db_name            = "quizdb"
  db_username        = var.db_username
  db_password        = var.db_password
  instance_class     = var.db_instance_class
}

module "elasticache" {
  source = "./modules/elasticache"

  project            = var.project
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  node_type          = var.redis_node_type
}

module "mq" {
  source = "./modules/mq"

  project            = var.project
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  private_subnet_ids = module.vpc.private_subnet_ids
  mq_username        = var.mq_username
  mq_password        = var.mq_password
  instance_type      = var.mq_instance_type
}

module "ecs" {
  source = "./modules/ecs"

  project            = var.project
  environment        = var.environment
  vpc_id             = module.vpc.vpc_id
  public_subnet_ids  = module.vpc.public_subnet_ids
  private_subnet_ids = module.vpc.private_subnet_ids
  backend_image      = var.backend_image
  frontend_image     = var.frontend_image

  db_url        = module.rds.jdbc_url
  db_username   = var.db_username
  db_password   = var.db_password
  redis_host    = module.elasticache.endpoint
  rabbitmq_host = module.mq.endpoint
  mq_username   = var.mq_username
  mq_password   = var.mq_password
  jwt_secret    = var.jwt_secret
}
