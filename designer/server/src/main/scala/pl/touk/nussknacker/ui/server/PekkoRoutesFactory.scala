package pl.touk.nussknacker.ui.server

import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.customhttpservice.PekkoCustomHttpServiceProvider
import pl.touk.nussknacker.ui.factory.{DomainServices, InfrastructureServices}
import pl.touk.nussknacker.ui.process.{ConfigScenarioToolbarService, ProcessStateDefinitionService}
import pl.touk.nussknacker.ui.process.deployment.{DeploymentService => LegacyDeploymentService}
import pl.touk.nussknacker.ui.process.migrate.{HttpRemoteEnvironment, ProcessModelMigrator, TestModelMigrations}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, ImpersonationSupport, LoggedUser}

import scala.concurrent.ExecutionContext

object PekkoRoutesFactory {

  def createRoutes(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      domainServices: DomainServices,
      authenticationResources: AuthenticationResources,
      customHttpServiceProviders: Map[String, PekkoCustomHttpServiceProvider]
  ): PekkoRoutes = {
    import infrastructureServices._

    val routesWithAuthentication = createRoutesWithAuthentication(
      designerConfig,
      infrastructureServices,
      domainServices,
      customHttpServiceProviders,
      authenticationResources.impersonationSupport
    )

    val routesWithoutAuthentication =
      createRoutesWithoutAuthentication(designerConfig, domainServices, authenticationResources)

    PekkoRoutes(routesWithAuthentication, routesWithoutAuthentication)
  }

  private def createRoutesWithAuthentication(
      designerConfig: DesignerConfig,
      infrastructureServices: InfrastructureServices,
      domainServices: DomainServices,
      customHttpServiceProviders: Map[String, PekkoCustomHttpServiceProvider],
      impersonationSupport: ImpersonationSupport
  ): List[RouteWithUser] = {
    import domainServices._
    import infrastructureServices._

    val processResources = {
      val configProcessToolbarService = new ConfigScenarioToolbarService(
        designerConfig.processToolbarConfig
      )
      new ProcessesResources(
        processService = processService,
        scenarioStatusProvider = scenarioStatusProvider,
        scenarioStatusPresenter = scenarioStatusPresenter,
        processToolbarService = configProcessToolbarService,
        processAuthorizer = processAuthorizer,
        processChangeListener = processChangeListener,
        scenarioActivityRepository = scenarioActivityRepository,
        dbioActionRunner = dbioRunner
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
        processingTypeServicesProvider.mapValues(_.additionalComponentConfigs),
        limitsService,
        processingTypeServicesProvider.mapValues(_.actionInfoService),
        liveDataRepository,
        dbioRunner,
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
      designerConfig.remoteEnvironment
        .map { remoteEnvironmentConfig =>
          val remoteEnvironment = new HttpRemoteEnvironment(
            remoteEnvironmentConfig,
            new TestModelMigrations(
              processingTypeServicesProvider
                .mapValues(_.designerModelData.modelData.migrations)
                .mapValues(new ProcessModelMigrator(_)),
              processingTypeServicesProvider.mapValues(_.scenarioValidator)
            ),
            designerConfig.environment,                  // local
            remoteEnvironmentConfig.targetEnvironmentId, // remote
            impersonationSupport
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
      designerConfig,
      authenticationResources.name,
      designerConfig.usageStatisticsReportsConfig,
      fingerprintService
    )
    List(
      settingsResources.publicRoute(),
      authenticationResources.routeWithPathPrefix,
    )
  }

  case class PekkoRoutes(routesWithAuthentication: List[RouteWithUser], routesWithoutAuthentication: List[Route])

}
