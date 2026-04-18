output "alb_dns_name" {
  description = "Application Load Balancer DNS"
  value       = module.ecs.alb_dns_name
}

output "rds_endpoint" {
  description = "RDS 엔드포인트"
  value       = module.rds.endpoint
}

output "redis_endpoint" {
  description = "ElastiCache 엔드포인트"
  value       = module.elasticache.endpoint
}

output "rabbitmq_endpoint" {
  description = "AmazonMQ 엔드포인트"
  value       = module.mq.endpoint
}
