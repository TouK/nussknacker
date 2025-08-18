package pl.touk.nussknacker.ui.server

import cats.Monoid
import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.customhttpservice._
import pl.touk.nussknacker.ui.customhttpservice.CustomHttpServiceProviderFactory.CustomHttpServiceProviderCreator._
import pl.touk.nussknacker.ui.customhttpservice.services.{NussknackerDomainServices, NussknackerInfrastructureServices}
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}
import pl.touk.nussknacker.ui.server.CustomHttpServiceProvidersLoader.CustomHttpServiceLoadingMode.{
  LoadServicesWithInfrastructureAndDomainDependencies,
  LoadServicesWithInfrastructureDependencies,
  LoadServicesWithoutDependencies
}

import scala.concurrent.ExecutionContext

object CustomHttpServiceProvidersLoader extends LazyLogging {

  def loadCustomHttpServiceProviders(
      designerConfig: DesignerConfig,
      loadingMode: CustomHttpServiceLoadingMode,
  ): Resource[IO, CustomHttpServiceProviders] = for {
    providerFactories <- Resource.eval(loadHttpServiceProviderFactories)
    customHttpServiceProviders <- createHttpServiceProviders(
      providerFactories,
      designerConfig,
      loadingMode,
    )
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
      loadingMode: CustomHttpServiceLoadingMode,
  ): Resource[IO, CustomHttpServiceProviders] = {
    customHttpServiceProviderFactories
      .traverse { factory =>
        createCustomHttpServiceProvider(designerConfig, factory, loadingMode) match {
          case Some(provider) => provider.map(Some(factory.name, _))
          case None           => Resource.pure[IO, Option[(String, CustomHttpServiceProvider)]](None)
        }
      }
      .map(_.flatten.toMap)
      .map { namedProviders =>
        namedProviders.foldLeft(CustomHttpServiceProviders(Map.empty, Map.empty)) {
          case (acc, (name, provider: PekkoCustomHttpServiceProvider)) =>
            acc.copy(pekko = acc.pekko + (name -> provider))
          case (acc, (name, provider: TapirCustomHttpServiceProvider)) =>
            acc.copy(tapir = acc.tapir + (name -> provider))
        }
      }
  }

  private def createCustomHttpServiceProvider(
      designerConfig: DesignerConfig,
      factory: CustomHttpServiceProviderFactory,
      loadingMode: CustomHttpServiceLoadingMode,
  ): Option[Resource[IO, CustomHttpServiceProvider]] = loadingMode match {
    case LoadServicesWithoutDependencies =>
      factory.creator match {
        case c: WithoutDependencies =>
          logger.info(s"Creating custom HTTP service ${factory.name} (without Nu dependencies)")
          Some(c.create(designerConfig.rawConfig))
        case _ =>
          None
      }
    case mode: LoadServicesWithInfrastructureDependencies =>
      factory.creator match {
        case c: WithInfrastructureDependencies =>
          logger.info(s"Creating custom HTTP service ${factory.name} (with Nu infrastructure dependencies)")
          Some(
            c.create(
              config = designerConfig.rawConfig,
              infrastructureServices = nuInfrastructureServices(mode.infrastructureServices)
            )
          )
        case _ =>
          None
      }
    case mode: LoadServicesWithInfrastructureAndDomainDependencies =>
      factory.creator match {
        case c: WithInfrastructureAndDomainDependencies =>
          logger.info(
            s"Creating custom HTTP service ${factory.name} (with Nu infrastructure and domain dependencies)"
          )
          Some(
            c.create(
              config = designerConfig.rawConfig,
              infrastructureServices = nuInfrastructureServices(mode.infrastructureServices),
              domainServices = nuDomainServices(mode.domainServices)(
                mode.infrastructureServices.executionContextWithIORuntime
              )
            )
          )
        case _ =>
          None
      }
  }

  private def nuInfrastructureServices(infrastructureServices: InfrastructureServices) = {
    new NussknackerInfrastructureServices(infrastructureServices.dbRef)
  }

  private def nuDomainServices(domainServices: DomainServices)(implicit executionContext: ExecutionContext) = {
    new NussknackerDomainServices(new ProcessServiceBasedScenarioServiceAdapter(domainServices.processService))
  }

  final case class CustomHttpServiceProviders(
      pekko: Map[String, PekkoCustomHttpServiceProvider],
      tapir: Map[String, TapirCustomHttpServiceProvider]
  )

  sealed trait CustomHttpServiceLoadingMode

  object CustomHttpServiceLoadingMode {

    case object LoadServicesWithoutDependencies extends CustomHttpServiceLoadingMode

    final case class LoadServicesWithInfrastructureDependencies(
        infrastructureServices: InfrastructureServices,
    ) extends CustomHttpServiceLoadingMode

    final case class LoadServicesWithInfrastructureAndDomainDependencies(
        infrastructureServices: InfrastructureServices,
        domainServices: DomainServices,
    ) extends CustomHttpServiceLoadingMode

  }

  object CustomHttpServiceProviders {

    implicit val monoid: Monoid[CustomHttpServiceProviders] =
      new Monoid[CustomHttpServiceProviders] {
        override def empty: CustomHttpServiceProviders =
          CustomHttpServiceProviders(Map.empty, Map.empty)

        override def combine(x: CustomHttpServiceProviders, y: CustomHttpServiceProviders): CustomHttpServiceProviders =
          CustomHttpServiceProviders(
            x.pekko ++ y.pekko,
            x.tapir ++ y.tapir
          )
      }

  }

}
