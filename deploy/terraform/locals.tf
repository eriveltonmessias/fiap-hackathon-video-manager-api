locals {
  template_vars = {
    apps_namespace        = var.apps_namespace
    aws_region            = var.aws_region
    image                 = var.image
    replicas              = var.replicas
    service_type          = var.service_type
    postgres_password     = var.postgres_password
    jwt_secret            = var.jwt_secret
    internal_api_key      = var.internal_api_key
    s3_access_key         = var.s3_access_key
    s3_secret_key         = var.s3_secret_key
    s3_endpoint           = "https://s3.${var.aws_region}.amazonaws.com"
    s3_input_bucket_name  = "${var.name_prefix}-videos-input"
    s3_output_bucket_name = "${var.name_prefix}-videos-output"
  }
}
