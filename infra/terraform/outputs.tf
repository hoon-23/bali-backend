output "vpc_id" {
  value = aws_vpc.main.id
}

output "public_subnet_ids" {
  value = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  value = aws_subnet.private[*].id
}

output "ecr_repository_url" {
  value = aws_ecr_repository.bali_api.repository_url
}

output "batch_ecr_repository_url" {
  value = aws_ecr_repository.bali_batch.repository_url
}

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "rds_credentials_secret_arn" {
  value = aws_db_instance.main.master_user_secret[0].secret_arn
}

output "alb_dns_name" {
  value = aws_lb.main.dns_name
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.bali_api.name
}

output "cloudfront_domain_name" {
  value = aws_cloudfront_distribution.main.domain_name
}

output "app_instance_id" {
  value = aws_instance.app.id
}

output "app_public_dns" {
  description = "CloudFront origin으로 쓸 EC2 퍼블릭 DNS"
  value       = aws_eip.app.public_dns
}

output "db_backup_bucket" {
  value = aws_s3_bucket.db_backup.bucket
}
