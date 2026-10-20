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

# Sign in with Apple 탈퇴 시 토큰 revoke용. 값은 Terraform 상태에 평문으로 남지 않도록 시크릿 껍데기만 만들고,
# 실제 값(Team ID/Key ID/.p8)은 AWS 콘솔이나 CLI로 직접 넣는다
resource "aws_secretsmanager_secret" "apple_team_id" {
  name = "${var.project}/apple-team-id"
}

resource "aws_secretsmanager_secret" "apple_key_id" {
  name = "${var.project}/apple-key-id"
}

resource "aws_secretsmanager_secret" "apple_private_key" {
  name = "${var.project}/apple-private-key"
}
