package pl.touk.nussknacker.ui.server

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProvider,
  CustomHttpServiceProviderFactory,
  ProcessServiceBasedScenarioServiceAdapter,
  TapirEndpointSupportAdapter
}
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.factory.DomainServices

object CustomHttpServiceProvidersLoader {

  def loadCustomHttpServiceProviders(designerConfig: DesignerConfig, domainServices: DomainServices)(
      implicit ec: ExecutionContextWithIORuntime
  ): Resource[IO, Map[String, CustomHttpServiceProvider]] = {
    val customHttpServiceProviderFactories = Multiplicity(
      ScalaServiceLoader.load[CustomHttpServiceProviderFactory](getClass.getClassLoader)
    ) match {
      case Empty() =>
        List.empty[CustomHttpServiceProviderFactory]
      case One(providerFactory) =>
        List(providerFactory)
      case Many(moreThanOne) if moreThanOne.map(_.name).distinct.size == moreThanOne.size =>
        moreThanOne
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(
          s"CustomHttpServiceProviderFactory instances with conflicting names found: $moreThanOne"
        )
    }
    lazy val nussknackerServices = new NussknackerServicesForCustomHttpService(
      new ProcessServiceBasedScenarioServiceAdapter(domainServices.processService),
      new TapirEndpointSupportAdapter
    )
    customHttpServiceProviderFactories
      .map { factory => factory.create(designerConfig.rawConfig, nussknackerServices).map(factory.name -> _) }
      .sequence
      .map(_.toMap)
  }

}
