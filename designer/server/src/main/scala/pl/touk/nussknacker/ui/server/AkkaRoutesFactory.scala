package pl.touk.nussknacker.ui.server

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.effect.{IO, Resource}
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.util.loader.ScalaServiceLoader
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.config.scenariotoolbar.CategoriesScenarioToolbarsConfigParser
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProvider,
  CustomHttpServiceProviderFactory,
  ProcessServiceBasedScenarioServiceAdapter
}
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}
import pl.touk.nussknacker.ui.process.{ConfigScenarioToolbarService, ProcessStateDefinitionService}
import pl.touk.nussknacker.ui.process.deployment.{DeploymentService => LegacyDeploymentService}
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, ProcessModelMigrator, TestModelMigrations}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}

import scala.concurrent.ExecutionContext

object AkkaRoutesFactory {

  def createRoutes(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      domainServices: DomainServices,
      authenticationResources: AuthenticationResources
  )(
      implicit system: ActorSystem,
      executionContext: ExecutionContext
  ): Resource[IO, AkkaRoutes] =
    for {
      customHttpServiceProviders <- createCustomHttpServiceProvider(designerConfig, domainServices)

      routesWithAuthentication = createRoutesWithAuthentication(
        designerConfig,
        infrastructureServices,
        domainServices,
        customHttpServiceProviders
      )

      routesWithoutAuthentication =
        createRoutesWithoutAuthentication(designerConfig, domainServices, authenticationResources)

    } yield AkkaRoutes(routesWithAuthentication, routesWithoutAuthentication)

  private def createCustomHttpServiceProvider(
      designerConfig: DesignerConfig,
      domainServices: DomainServices
  )(implicit ec: ExecutionContext): Resource[IO, Map[String, CustomHttpServiceProvider]] = {
    lazy val nussknackerServices = new NussknackerServicesForCustomHttpService(
      new ProcessServiceBasedScenarioServiceAdapter(domainServices.processService)
    )

    loadCustomHttpServiceProviderFactories()
      .map { factory => factory.create(designerConfig.rawConfig, nussknackerServices).map(factory.name -> _) }
      .sequence
      .map(_.toMap)
  }

  private def loadCustomHttpServiceProviderFactories(): List[CustomHttpServiceProviderFactory] = {
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

  private def createRoutesWithAuthentication(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      domainServices: DomainServices,
      customHttpServiceProviders: Map[String, CustomHttpServiceProvider]
  )(
      implicit system: ActorSystem,
      executionContext: ExecutionContext,
  ): List[RouteWithUser] = {
    import domainServices._
    import infrastructureServices._

    val processResources = {
      val configProcessToolbarService = new ConfigScenarioToolbarService(
        CategoriesScenarioToolbarsConfigParser.parse(designerConfig.rawConfig)
      )
      new ProcessesResources(
        processService = processService,
        scenarioStatusProvider = scenarioStatusProvider,
        scenarioStatusPresenter = scenarioStatusPresenter,
        processToolbarService = configProcessToolbarService,
        processAuthorizer = processAuthorizer,
        processChangeListener = processChangeListener
      )
    }
    val processExportResources = new ProcessesExportResources(
      futureProcessRepository,
      processService,
      scenarioActivityRepository,
      processingTypeServicesProvider.mapValues(_.processResolver),
      dbioRunner,
    )
    val managementResources = {
      val legacyDeploymentService = new LegacyDeploymentService(
        dmDispatcher,
        processingTypeServicesProvider.mapValues(_.scenarioValidator),
        processingTypeServicesProvider.mapValues(_.scenarioResolver),
        actionService,
        processingTypeServicesProvider.mapValues(_.additionalComponentConfigs)
      )
      new ManagementResources(
        processAuthorizer,
        processService,
        legacyDeploymentService,
        dmDispatcher,
        metricsRegistry,
        processingTypeServicesProvider.mapValues(_.scenarioTestService),
      )
    }
    val validationResource =
      new ValidationResources(processService, processingTypeServicesProvider.mapValues(_.processResolver))
    val definitionResources = new DefinitionResources(
      processingTypeServicesProvider.mapValues(_.definitionService)
    )
    val statusResources = {
      val stateDefinitionService = new ProcessStateDefinitionService(
        processingTypeServicesProvider
          .mapValues(_.category)
          .mapCombined(_.statusNameToStateDefinitionsMapping)
      )
      new StatusResources(stateDefinitionService)
    }
    val routes = List(
      processResources,
      processExportResources,
      managementResources,
      validationResource,
      definitionResources,
      statusResources,
    )

    val optionalRoutes = List(
      designerConfig.featureTogglesConfig.remoteEnvironment
        .map { migrationConfig =>
          val remoteEnvironment = new HttpRemoteEnvironment(
            migrationConfig,
            new TestModelMigrations(
              processingTypeServicesProvider
                .mapValues(_.designerModelData.modelData.migrations)
                .mapValues(new ProcessModelMigrator(_)),
              processingTypeServicesProvider.mapValues(_.scenarioValidator)
            ),
            designerConfig.environment
          )
          new RemoteEnvironmentResources(
            remoteEnvironment,
            processService,
            processAuthorizer,
            scenarioActivityRepository,
            dbioRunner,
            clock,
          )
        },
      countsReporter.map { reporter =>
        new ProcessReportResources(reporter, counter, futureProcessRepository, processService)
      },
    ).flatten

    val customHttpServiceRoutes = customHttpServiceProviders.map { case (name, provider) =>
      new RouteWithUser {
        override protected def securedRoute(implicit user: LoggedUser): Route =
          pathPrefix("custom" / name) {
            provider.provideRouteWithUser(user)
          }
      }
    }

    routes ++ optionalRoutes ++ customHttpServiceRoutes
  }

  private def createRoutesWithoutAuthentication(
      designerConfig: DesignerConfig,
      domainServices: DomainServices,
      authenticationResources: AuthenticationResources,
  )(
      implicit executionContext: ExecutionContext
  ): List[Route] = {
    import domainServices._

    // TODO: WARNING now all settings are available for not sign in user. In future we should show only basic settings
    val settingsResources = new SettingsResources(
      designerConfig.featureTogglesConfig,
      authenticationResources.name,
      designerConfig.usageStatisticsReportsConfig,
      fingerprintService
    )
    List(
      settingsResources.publicRoute(),
      authenticationResources.routeWithPathPrefix,
    )
  }

  case class AkkaRoutes(routesWithAuthentication: List[RouteWithUser], routesWithoutAuthentication: List[Route])

}
