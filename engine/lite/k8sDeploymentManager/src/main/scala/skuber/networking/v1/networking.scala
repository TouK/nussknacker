package skuber.networking

import skuber.ListResource

package object v1 {
  val ingressAPIVersion = "networking.k8s.io/v1"

  type IngressList = ListResource[Ingress]
}
