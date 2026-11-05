variable "aws_region" {
  description = "리소스를 생성할 AWS 리전"
  type        = string
  default     = "ap-northeast-2"
}

variable "project" {
  description = "리소스 이름 접두사"
  type        = string
  default     = "bali-dev"
}

variable "google_client_id" {
  description = "Google OAuth client-id (dev)"
  type        = string
  sensitive   = true
}

variable "apple_bundle_id" {
  description = "Apple bundle id (dev)"
  type        = string
  sensitive   = true
}

variable "kakao_client_id" {
  description = "Kakao OAuth client-id (dev)"
  type        = string
  sensitive   = true
}

variable "alert_email" {
  description = "장애 알람(SNS)을 받을 이메일 주소"
  type        = string
  sensitive   = true
}
