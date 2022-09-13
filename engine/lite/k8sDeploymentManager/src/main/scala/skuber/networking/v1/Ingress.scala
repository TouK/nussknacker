package skuber.networking.v1

import play.api.libs.json.{Format, JsPath, Json}
import skuber.ResourceSpecification.{Names, Scope}
import skuber.networking.v1.Ingress.PathType.{ImplementationSpecific, PathType}
import skuber.{NameablePort, NonCoreResourceSpecification, ObjectMeta, ObjectResource, ResourceDefinition}

import scala.util.Try

case class Ingress(
                    kind: String = "Ingress",
                    override val apiVersion: String = ingressAPIVersion,
                    metadata: ObjectMeta = ObjectMeta(),
                    spec: Option[Ingress.Spec] = None,
                    status: Option[Ingress.Status] = None)
  extends ObjectResource {

  import Ingress.Backend

  lazy val copySpec: Ingress.Spec = this.spec.getOrElse(new Ingress.Spec)

  /**
    * Fluent API method for building out ingress rules e.g.
    * {{{
    * val ingress = Ingress("microservices").
    *   addHttpRule("foo.bar.com",
    *               ImplementationSpecific,
    *               Map("/order" -> "orderService:80",
    *                   "inventory" -> "inventoryService:80")).
    *   addHttpRule("foo1.bar.com",
    *                Map("/ship" -> "orderService:80",
    *                    "inventory" -> "inventoryService:80")).
    * }}}
    */
  def addHttpRule(host: String, pathType: PathType, pathsMap: Map[String, String]): Ingress =
    addHttpRule(Some(host), pathType: PathType, pathsMap)

  /**
    * Fluent API method for building out ingress rules without host e.g.
    * {{{
    * val ingress = Ingress("microservices").
    *   addHttpRule(ImplementationSpecific,
    *               Map("/order" -> "orderService:80",
    *                   "inventory" -> "inventoryService:80")).
    *   addHttpRule(ImplementationSpecific,
    *               Map("/ship" -> "orderService:80",
    *                   "inventory" -> "inventoryService:80")).
    * }}}
    */
  def addHttpRule(pathType: PathType, pathsMap: Map[String, String]): Ingress =
    addHttpRule(Option.empty, pathType: PathType, pathsMap)

  /**
    * Fluent API method for building out ingress rules e.g.
    * {{{
    * val ingress =
    *   Ingress("microservices")
    *     .addHttpRule("foo.bar.com",
    *                  ImplementationSpecific,
    *                  "/order" -> "orderService:80",
    *                  "inventory" -> "inventoryService:80")
    *     .addHttpRule("foo1.bar.com",
    *                  ImplementationSpecific,
    *                  "/ship" -> "orderService:80",
    *                  "inventory" -> "inventoryService:80").
    * }}}
    */
  def addHttpRule(host: String, pathType: PathType, pathsMap: (String, String)*): Ingress =
    addHttpRule(Some(host), pathType: PathType, pathsMap.toMap)

  /**
    * Fluent API method for building out ingress rules without host e.g.
    * {{{
    * val ingress =
    *   Ingress("microservices")
    *     .addHttpRule(ImplementationSpecific,
    *                  "/order" -> "orderService:80",
    *                  "inventory" -> "inventoryService:80")
    *     .addHttpRule(ImplementationSpecific,
    *                  "/ship" -> "orderService:80",
    *                  "inventory" -> "inventoryService:80").
    * }}}
    */
  def addHttpRule(pathType: PathType, pathsMap: (String, String)*): Ingress =
    addHttpRule(Option.empty, pathType, pathsMap.toMap)

  private val backendSpec = "(\\S+):(\\S+)".r

  /**
    * Fluent API method for building out ingress rules e.g.
    * {{{
    * val ingress =
    *   Ingress("microservices")
    *     .addHttpRule(Some("foo.bar.com"),
    *                  Exact,
    *                  Map("/order" -> "orderService:80",
    *                      "inventory" -> "inventoryService:80"))
    *     .addHttpRule(None,
    *                  ImplementationSpecific,
    *                  Map("/ship" -> "orderService:80",
    *                      "inventory" -> "inventoryService:80")).
    * }}}
    */
  def addHttpRule(host: Option[String], pathType: PathType, pathsMap: Map[String, String]): Ingress = {
    val paths: List[Ingress.Path] = pathsMap.map {
      case (path: String, backendService: String) =>
        backendService match {
          case backendSpec(serviceName, servicePort) =>
            Ingress.Path(
              path,
              Ingress.Backend(
                Option(Ingress.ServiceType(serviceName, toIngressPort(toNameablePort(servicePort))))
              ),
              pathType
            )
          case _ =>
            throw new Exception(
              s"invalid backend format: expected 'serviceName:servicePort' (got '$backendService', for host: $host)"
            )
        }

    }.toList
    val httpRule                  = Ingress.HttpRule(paths)
    val rule                      = Ingress.Rule(host, httpRule)
    val withRuleSpec              = copySpec.copy(rules = copySpec.rules :+ rule)

    this.copy(spec = Some(withRuleSpec))
  }

  /**
    * set the default backend i.e. if no ingress rule matches the incoming traffic then it gets routed to the specified service
    *
    * @param serviceNameAndPort - service name and port as 'serviceName:servicePort'
    * @return copy of this Ingress with default backend set
    */
  def withDefaultBackendService(serviceNameAndPort: String): Ingress = {
    serviceNameAndPort match {
      case backendSpec(serviceName, servicePort) =>
        withDefaultBackendService(serviceName, toNameablePort(servicePort))
      case _ =>
        throw new Exception(s"invalid default backend format: expected 'serviceName:servicePort' (got '$serviceNameAndPort')")
    }
  }

  /**
    * set the default backend i.e. if no ingress rule matches the incoming traffic then it gets routed to the specified service
    *
    * @param serviceName - service name
    * @param servicePort - service port
    * @return copy of this Ingress with default backend set
    */
  def withDefaultBackendService(serviceName: String, servicePort: NameablePort): Ingress = {
    val be = Backend(Option(Ingress.ServiceType(serviceName, toIngressPort(servicePort))))
    this.copy(spec = Some(copySpec.copy(backend = Some(be))))
  }

  def addAnnotations(newAnnos: Map[String, String]): Ingress =
    this.copy(metadata = this.metadata.copy(annotations = this.metadata.annotations ++ newAnnos))

  private def toIngressPort(port: NameablePort): Ingress.Port = port match {
    case Left(value) => Ingress.Port(number = Some(value))
    case Right(value) => Ingress.Port(name = Some(value))
  }

  private def toNameablePort(port: String): NameablePort =
    Try(port.toInt).toEither.left.map(_ => port).swap
}

object Ingress {
  val specification: NonCoreResourceSpecification = NonCoreResourceSpecification(
    apiGroup = "networking.k8s.io",
    version = "v1",
    scope = Scope.Namespaced,
    names = Names(
      plural = "ingresses",
      singular = "ingress",
      kind = "Ingress",
      shortNames = List("ing")
    )
  )

  implicit val ingDef    : ResourceDefinition[Ingress]     = new ResourceDefinition[Ingress] {
    def spec: NonCoreResourceSpecification = specification
  }
  implicit val ingListDef: ResourceDefinition[IngressList] = new ResourceDefinition[IngressList] {
    def spec: NonCoreResourceSpecification = specification
  }

  def apply(name: String): Ingress = Ingress(metadata = ObjectMeta(name = name))

  case class Port(name: Option[String] = None, number: Option[Int] = None)
  case class ServiceType(name: String, port: Port)

  // Backend contains either service or resource
  case class Backend(service: Option[ServiceType] = None, resource: Option[String] = None)
  case class Path(path: String, backend: Backend, pathType: PathType = ImplementationSpecific)
  case class HttpRule(paths: List[Path] = List())
  case class Rule(host: Option[String], http: HttpRule)
  case class TLS(hosts: List[String] = List(), secretName: Option[String] = None)

  object PathType extends Enumeration {
    type PathType = Value
    val ImplementationSpecific, Exact, Prefix = Value
  }

  case class Spec(
                   backend: Option[Backend] = None,
                   rules: List[Rule] = List(),
                   tls: List[TLS] = List(),
                   ingressClassName: Option[String] = None)

  case class Status(loadBalancer: Option[Status.LoadBalancer] = None)

  object Status {
    case class LoadBalancer(ingress: List[LoadBalancer.Ingress])
    object LoadBalancer {
      case class Ingress(ip: Option[String] = None, hostName: Option[String] = None)
    }
  }

  // json formatters

  import play.api.libs.functional.syntax._
  import skuber.json.format._

  implicit val ingressPortFmt: Format[Ingress.Port] = Json.format[Ingress.Port]

  implicit val ingressServiceFmt: Format[Ingress.ServiceType] = (
    (JsPath \ "name").format[String] and
      (JsPath \ "port").format[Ingress.Port]
    ) (Ingress.ServiceType.apply _, unlift(Ingress.ServiceType.unapply))

  implicit val ingressBackendFmt: Format[Ingress.Backend] = (
    (JsPath \ "service").formatNullable[Ingress.ServiceType] and
      (JsPath \ "resource").formatNullable[String]
    ) (Ingress.Backend.apply _, unlift(Ingress.Backend.unapply))

  implicit val ingressPathFmt: Format[Ingress.Path] = (
    (JsPath \ "path").formatMaybeEmptyString() and
      (JsPath \ "backend").format[Ingress.Backend] and
      (JsPath \ "pathType").formatEnum(PathType, Some(PathType.ImplementationSpecific))
    ) (Ingress.Path.apply _, unlift(Ingress.Path.unapply))

  implicit val ingressHttpRuledFmt: Format[Ingress.HttpRule] = Json.format[Ingress.HttpRule]
  implicit val ingressRuleFmt     : Format[Ingress.Rule]     = Json.format[Ingress.Rule]
  implicit val ingressTLSFmt      : Format[Ingress.TLS]      = Json.format[Ingress.TLS]


  implicit val ingressSpecFormat: Format[Ingress.Spec] = (
    (JsPath \ "defaultBackend").formatNullable[Ingress.Backend] and
      (JsPath \ "rules").formatMaybeEmptyList[Ingress.Rule] and
      (JsPath \ "tls").formatMaybeEmptyList[Ingress.TLS] and
      (JsPath \ "ingressClassName").formatNullable[String]
    ) (Ingress.Spec.apply _, unlift(Ingress.Spec.unapply))


  implicit val ingrlbingFormat: Format[Ingress.Status.LoadBalancer.Ingress] =
    Json.format[Ingress.Status.LoadBalancer.Ingress]

  implicit val ingrlbFormat: Format[Ingress.Status.LoadBalancer] =
    (JsPath \ "ingress").formatMaybeEmptyList[Ingress.Status.LoadBalancer.Ingress].inmap(
      ings => Ingress.Status.LoadBalancer(ings),
      lb => lb.ingress
    )

  implicit val ingressStatusFormat: Format[Ingress.Status] = Json.format[Ingress.Status]

  implicit lazy val ingressFormat: Format[Ingress] = (
    objFormat and
      (JsPath \ "spec").formatNullable[Ingress.Spec] and
      (JsPath \ "status").formatNullable[Ingress.Status]
    ) (Ingress.apply _, unlift(Ingress.unapply))

  implicit val ingressListFmt: Format[IngressList] = ListResourceFormat[Ingress]

}