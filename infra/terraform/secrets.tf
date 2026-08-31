resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name = "${var.project}/jwt-secret"
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = random_password.jwt_secret.result
}

resource "aws_secretsmanager_secret" "google_client_id" {
  name = "${var.project}/google-client-id"
}

resource "aws_secretsmanager_secret_version" "google_client_id" {
  secret_id     = aws_secretsmanager_secret.google_client_id.id
  secret_string = var.google_client_id
}

resource "aws_secretsmanager_secret" "apple_bundle_id" {
  name = "${var.project}/apple-bundle-id"
}

resource "aws_secretsmanager_secret_version" "apple_bundle_id" {
  secret_id     = aws_secretsmanager_secret.apple_bundle_id.id
  secret_string = var.apple_bundle_id
}

resource "aws_secretsmanager_secret" "kakao_client_id" {
  name = "${var.project}/kakao-client-id"
}

resource "aws_secretsmanager_secret_version" "kakao_client_id" {
  secret_id     = aws_secretsmanager_secret.kakao_client_id.id
  secret_string = var.kakao_client_id
}
