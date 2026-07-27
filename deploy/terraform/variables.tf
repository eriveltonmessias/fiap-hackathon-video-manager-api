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
  default = "LoadBalancer"
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
