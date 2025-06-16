package pl.touk.nussknacker.ui.server

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProviderFactory,
  PekkoCustomHttpServiceProvider,
  ProcessServiceBasedScenarioServiceAdapter,
  TapirCustomHttpServiceProvider
}
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}

import scala.concurrent.ExecutionContext

object CustomHttpServiceProvidersLoader {

  def loadCustomHttpServiceProviders(
      designerConfig: DesignerConfig,
      domainServices: DomainServices,
      infrastructureServices: InfrastructureServices
  )(
      implicit executionContext: ExecutionContext
  ): Resource[IO, CustomHttpServiceProviders] = for {
    providerFactories <- Resource.eval(loadHttpServiceProviderFactories)
    customHttpServiceProviders <- createHttpServiceProviders(
      providerFactories,
      designerConfig,
      domainServices,
      infrastructureServices
    )(executionContext)
  } yield customHttpServiceProviders

  private def loadHttpServiceProviderFactories: IO[List[CustomHttpServiceProviderFactory]] = {
    IO {
      Multiplicity(
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
    }
  }

  private def createHttpServiceProviders(
      customHttpServiceProviderFactories: List[CustomHttpServiceProviderFactory],
      designerConfig: DesignerConfig,
      domainServices: DomainServices,
      infrastructureServices: InfrastructureServices
  )(implicit executionContext: ExecutionContext): Resource[IO, CustomHttpServiceProviders] = {
    lazy val nussknackerServices = new NussknackerServicesForCustomHttpService(
      new ProcessServiceBasedScenarioServiceAdapter(domainServices.processService),
      infrastructureServices.dbRef
    )
    customHttpServiceProviderFactories
      .traverse { factory => factory.create(designerConfig.rawConfig, nussknackerServices).map(factory.name -> _) }
      .map { namedProviders =>
        namedProviders.foldLeft(CustomHttpServiceProviders(Map.empty, Map.empty)) {
          case (acc, (name, provider: PekkoCustomHttpServiceProvider)) =>
            acc.copy(pekko = acc.pekko + (name -> provider))
          case (acc, (name, provider: TapirCustomHttpServiceProvider)) =>
            acc.copy(tapir = acc.tapir + (name -> provider))
        }
      }
  }

  final case class CustomHttpServiceProviders(
      pekko: Map[String, PekkoCustomHttpServiceProvider],
      tapir: Map[String, TapirCustomHttpServiceProvider]
  )

}
