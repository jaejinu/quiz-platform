variable "project" {
  description = "프로젝트 이름"
  type        = string
  default     = "quiz-platform"
}

variable "environment" {
  description = "환경 (dev/staging/prod)"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "vpc_cidr" {
  description = "VPC CIDR 블록"
  type        = string
  default     = "10.0.0.0/16"
}

variable "db_username" {
  description = "RDS 사용자명"
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "RDS 비밀번호"
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t3.micro"
}

variable "redis_node_type" {
  description = "ElastiCache 노드 타입"
  type        = string
  default     = "cache.t3.micro"
}

variable "mq_username" {
  description = "AmazonMQ 사용자명"
  type        = string
  sensitive   = true
}

variable "mq_password" {
  description = "AmazonMQ 비밀번호"
  type        = string
  sensitive   = true
}

variable "mq_instance_type" {
  description = "AmazonMQ 인스턴스 타입"
  type        = string
  default     = "mq.t3.micro"
}

variable "backend_image" {
  description = "백엔드 컨테이너 이미지"
  type        = string
  default     = "ghcr.io/jaejinu/quiz-platform-backend:latest"
}

variable "frontend_image" {
  description = "프론트엔드 컨테이너 이미지"
  type        = string
  default     = "ghcr.io/jaejinu/quiz-platform-frontend:latest"
}

variable "jwt_secret" {
  description = "JWT 서명 시크릿 (최소 32바이트)"
  type        = string
  sensitive   = true
}
