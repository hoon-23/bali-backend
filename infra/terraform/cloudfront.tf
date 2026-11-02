resource "aws_cloudfront_distribution" "main" {
  enabled = true
  comment = "${var.project} bali-api"

  origin {
    domain_name = aws_lb.main.dns_name
    origin_id   = "alb-origin"

    # ALB가 이 헤더를 검증해 CloudFront를 거치지 않은 직접 호출을 거부한다 (alb.tf 리스너 규칙)
    custom_header {
      name  = "X-Origin-Verify"
      value = random_password.origin_verify.result
    }

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "alb-origin"
    viewer_protocol_policy = "redirect-to-https"

    # API 응답은 대부분 사용자별 동적 데이터라 기본적으로 캐시하지 않는다
    forwarded_values {
      query_string = true
      headers      = ["Authorization", "Content-Type"]
      cookies {
        forward = "all"
      }
    }
    min_ttl     = 0
    default_ttl = 0
    max_ttl     = 0
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 커스텀 도메인을 붙이지 않는 한 CloudFront 기본 인증서(*.cloudfront.net)를 그대로 사용한다
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
