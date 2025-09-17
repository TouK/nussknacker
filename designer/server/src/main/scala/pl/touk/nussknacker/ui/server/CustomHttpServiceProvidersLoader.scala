package pl.touk.nussknacker.ui.server

import cats.effect.IO
import cats.effect.kernel.Resource
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.restmodel.definition
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProviderFactory,
  PekkoCustomHttpServiceProvider,
  ProcessServiceBasedScenarioServiceAdapter,
  TapirCustomHttpServiceProvider
}
import pl.touk.nussknacker.ui.customhttpservice.services.{
  DefinitionsServiceForHttpService,
  NussknackerServicesForCustomHttpService,
  ProcessingTypeServicesProvider
}
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.definition.DefinitionsService.ComponentUiConfigMode.{BasicConfig, EnrichedWithUiConfig}
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}
import pl.touk.nussknacker.ui.process.processingtype.{CombinedProcessingTypeData, ProcessingTypeServices}
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}

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
      infrastructureServices.dbRef,
      new ProcessingTypeServicesProviderImpl(domainServices.processingTypeServicesProvider)
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

  private class ProcessingTypeServicesProviderImpl(
      processingTypeServicesProvider: ProcessingTypeDataProvider[ProcessingTypeServices, CombinedProcessingTypeData]
  ) extends ProcessingTypeServicesProvider {

    // todo: rethink what can be memoized here
    override def definitionService: DefinitionsServiceForHttpService = new DefinitionsServiceForHttpService {
      override def prepareUIDefinitions(
          processingType: ProcessingType,
          forFragment: Boolean,
          componentUiConfigMode: DefinitionsServiceForHttpService.ExternalComponentUiConfigMode
      )(implicit user: LoggedUser): Future[definition.UIDefinitions] = {
        val mappedToInternalConfigMode: DefinitionsService.ComponentUiConfigMode = componentUiConfigMode match {
          case DefinitionsServiceForHttpService.ExternalComponentUiConfigMode.EnrichedWithUiConfig =>
            EnrichedWithUiConfig
          case DefinitionsServiceForHttpService.ExternalComponentUiConfigMode.BasicConfig => BasicConfig
        }
        processingTypeServicesProvider
          .forProcessingTypeUnsafe(processingType)
          .definitionService
          .prepareUIDefinitions(
            processingType,
            forFragment,
            mappedToInternalConfigMode
          )
      }
    }

  }

}
