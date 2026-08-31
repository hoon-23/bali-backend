terraform {
  backend "s3" {
    bucket         = "bali-dev-terraform-state"
    key            = "dev/terraform.tfstate"
    region         = "ap-northeast-2"
    dynamodb_table = "bali-dev-terraform-lock"
    encrypt        = true
  }
}
