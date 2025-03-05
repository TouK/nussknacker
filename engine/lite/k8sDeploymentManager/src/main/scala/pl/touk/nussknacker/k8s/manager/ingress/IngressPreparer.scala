package pl.touk.nussknacker.k8s.manager.ingress

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import monocle.macros.GenLens
import monocle.std.option.some
import pl.touk.nussknacker.engine.api.{LiteStreamMetaData, ProcessVersion, RequestResponseMetaData, TypeSpecificData}
import pl.touk.nussknacker.k8s.manager.{K8sDeploymentManager, RequestResponseSlugUtils}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.labelsForScenario
import pl.touk.nussknacker.k8s.manager.ingress.IngressPreparer.rewriteAnnotation
import play.api.libs.json.Json
import skuber.networking.v1.Ingress

case class IngressConfig(
    enabled: Boolean = false,
    host: Option[String] = None,
    rootPath: String = "/",
    config: Config = ConfigFactory.empty()
)

class IngressPreparer(config: IngressConfig, nuInstanceName: Option[String]) {

  def prepare(
      processVersion: ProcessVersion,
      typeSpecificData: TypeSpecificData,
      serviceName: String,
      servicePort: Int
  ): Option[Ingress] =
    typeSpecificData match {
      case _: LiteStreamMetaData => None
      case rrMetaData: RequestResponseMetaData if config.enabled =>
        Some(prepareRequestResponseIngress(processVersion, rrMetaData, serviceName, servicePort))
      case _ if !config.enabled => None
      case other                => throw new IllegalArgumentException("Not supported scenario meta data type: " + other)
    }

  private def prepareRequestResponseIngress(
      processVersion: ProcessVersion,
      rrMetaData: RequestResponseMetaData,
      serviceName: String,
      servicePort: Int
  ): Ingress = {
    val objectName = K8sDeploymentManager.objectNameForScenario(processVersion, nuInstanceName, None)
    val labels     = labelsForScenario(processVersion, nuInstanceName)
    val slug       = RequestResponseSlugUtils.determineSlug(processVersion.processName, rrMetaData, nuInstanceName)

    // we use 'OptionOptics some' here and do not worry about withDefault because _.spec is provided in defaultMinimalIngress
    val ingressSpecLens = GenLens[Ingress](_.spec) composePrism some

    val lens = GenLens[Ingress](_.metadata.name).set(objectName) andThen
      GenLens[Ingress](_.metadata.labels).modify(_ ++ labels) andThen
      GenLens[Ingress](_.metadata.annotations).modify(_ ++ rewriteAnnotation) andThen
      (ingressSpecLens composeLens GenLens[Ingress.Spec](_.rules)).modify(
        _ ++ List(
          Ingress.Rule(
            config.host,
            Ingress.HttpRule(paths =
              List(
                Ingress.Path(
                  path =
                    s"${config.rootPath}$slug" + "(/|$)(.*)", // TODO: fix RequestResponseOpenApiGenerator so it's aware of ingress address
                  backend =
                    Ingress.Backend(Some(Ingress.ServiceType(serviceName, Ingress.Port(number = Some(servicePort))))),
                  pathType = Ingress.PathType.Prefix
                )
              )
            )
          )
        )
      )

    lens(fromUserConfig)
  }

  private def fromUserConfig: Ingress = {
    val minimalConfig = ConfigFactory.parseResources(getClass.getClassLoader, "defaultMinimalIngress.conf")
    val finalConfig   = config.config.withFallback(minimalConfig)
    Json.parse(finalConfig.root().render(ConfigRenderOptions.concise())).as[Ingress]
  }

}

object IngressPreparer {
  private[ingress] val rewriteAnnotation = Map("nginx.ingress.kubernetes.io/rewrite-target" -> "/$2")
}
