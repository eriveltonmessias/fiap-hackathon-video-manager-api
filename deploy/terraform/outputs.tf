output "cluster_name" {
  value = data.terraform_remote_state.infra.outputs.cluster_name
}

output "public_api_base_url" {
  description = "Current public Kong Load Balancer URL used in notifications."
  value       = local.public_api_base_url
}
