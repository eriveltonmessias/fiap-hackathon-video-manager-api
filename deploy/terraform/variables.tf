variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "state_bucket" {
  type = string
}

variable "image_tag" {
  type = string
}

variable "replicas" {
  type    = number
  default = 1
}

variable "service_type" {
  type    = string
  default = "ClusterIP"
}

variable "mail_host" {
  type = string
}

variable "mail_port" {
  type    = number
  default = 587
}

variable "notification_email_from" {
  type = string
}

variable "mail_username" {
  type      = string
  sensitive = true
}

variable "mail_password" {
  type      = string
  sensitive = true
}

variable "postgres_password" {
  type      = string
  sensitive = true
}

variable "jwt_secret" {
  type      = string
  sensitive = true
}

variable "internal_api_key" {
  type      = string
  sensitive = true
}

variable "s3_access_key" {
  type      = string
  sensitive = true
}

variable "s3_secret_key" {
  type      = string
  sensitive = true
}
