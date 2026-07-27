locals {
  template_vars = {
    apps_namespace          = data.terraform_remote_state.data_platform.outputs.apps_namespace
    aws_region              = var.aws_region
    image                   = "${data.terraform_remote_state.infra.outputs.ecr_repository_urls["video-manager-api"]}:${var.image_tag}"
    replicas                = var.replicas
    service_type            = var.service_type
    postgres_password       = jsonencode(var.postgres_password)
    jwt_secret              = jsonencode(var.jwt_secret)
    internal_api_key        = jsonencode(var.internal_api_key)
    s3_access_key           = jsonencode(var.s3_access_key)
    s3_secret_key           = jsonencode(var.s3_secret_key)
    s3_endpoint             = "https://s3.${var.aws_region}.amazonaws.com"
    s3_input_bucket_name    = data.terraform_remote_state.data_platform.outputs.s3_input_bucket
    s3_output_bucket_name   = data.terraform_remote_state.data_platform.outputs.s3_output_bucket
    public_api_base_url     = local.public_api_base_url
    mail_host               = var.mail_host
    mail_port               = var.mail_port
    notification_email_from = var.notification_email_from
    mail_username           = jsonencode(var.mail_username)
    mail_password           = jsonencode(var.mail_password)
    config_checksum = sha256(join("|", [
      local.public_api_base_url,
      var.mail_host,
      tostring(var.mail_port),
      var.notification_email_from,
    ]))
    secret_checksum = nonsensitive(sha256(join("|", [
      var.postgres_password,
      var.jwt_secret,
      var.internal_api_key,
      var.s3_access_key,
      var.s3_secret_key,
      var.mail_username,
      var.mail_password,
    ])))
  }
}
