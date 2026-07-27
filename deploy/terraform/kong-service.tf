data "kubernetes_service_v1" "kong_proxy" {
  metadata {
    name      = data.terraform_remote_state.infra.outputs.kong_proxy_service_name
    namespace = data.terraform_remote_state.infra.outputs.kong_namespace
  }
}

locals {
  kong_load_balancer_ingress = data.kubernetes_service_v1.kong_proxy.status[0].load_balancer[0].ingress[0]
  kong_load_balancer_address = coalesce(
    local.kong_load_balancer_ingress.hostname,
    local.kong_load_balancer_ingress.ip,
  )
  public_api_base_url = "http://${local.kong_load_balancer_address}"
}
