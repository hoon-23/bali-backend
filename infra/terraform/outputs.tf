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

output "rds_endpoint" {
  value = aws_db_instance.main.address
}

output "rds_credentials_secret_arn" {
  value = aws_db_instance.main.master_user_secret[0].secret_arn
}
