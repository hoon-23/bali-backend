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
