package pl.touk.nussknacker.ui.statistics

import cats.data.EitherT
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, StateStatus}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.graph.node.FragmentInput
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.db.timeseries.{FEStatisticsRepository, ReadFEStatisticsRepository}
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.processingtype.{DeploymentManagerType, ProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.process.repository.{DbProcessActivityRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsService.nuFingerprintFileName

import java.time.{Clock, Instant}
import scala.concurrent.{ExecutionContext, Future}

object UsageStatisticsReportsSettingsService extends LazyLogging {

  private val nuFingerprintFileName = new FileName("nussknacker.fingerprint")

  def apply(
      config: UsageStatisticsReportsConfig,
      urlConfig: StatisticUrlConfig,
      processService: ProcessService,
      // TODO: Instead of passing deploymentManagerTypes next to processService, we should split domain ScenarioWithDetails from DTOs - see comment in ScenarioWithDetails
      deploymentManagerTypes: ProcessingTypeDataProvider[DeploymentManagerType, _],
      fingerprintService: FingerprintService,
      scenarioActivityRepository: ProcessActivityRepository,
      // TODO: Should not depend on DTO, need to extract usageCount and check if all available components are present using processingTypeDataProvider
      componentService: ComponentService,
      statisticsRepository: FEStatisticsRepository[Future],
      componentList: List[ComponentDefinitionWithImplementation],
      designerClock: Clock
  )(implicit ec: ExecutionContext): UsageStatisticsReportsSettingsService = {
    val ignoringErrorsFEStatisticsRepository = new IgnoringErrorsFEStatisticsRepository(statisticsRepository)
    implicit val user: LoggedUser            = NussknackerInternalUser.instance

    def fetchNonArchivedScenarioParameters(): Future[Either[StatisticError, List[ScenarioStatisticsInputData]]] = {
      // TODO: Warning, here is a security leak. We report statistics in the scope of processing types to which
      //       given user has no access rights.
      val deploymentManagerTypeByProcessingType = deploymentManagerTypes.all(user)
      processService
        .getLatestProcessesWithDetails(
          ScenarioQuery.unarchived,
          GetScenarioWithDetailsOptions.withsScenarioGraph.withFetchState
        )(user)
        .map { scenariosDetails =>
          Right(
            scenariosDetails.map(scenario =>
              ScenarioStatisticsInputData(
                isFragment = scenario.isFragment,
                processingMode = scenario.processingMode,
                deploymentManagerType = deploymentManagerTypeByProcessingType(scenario.processingType),
                status = scenario.state.map(_.status),
                nodesCount = scenario.scenarioGraph.map(_.nodes.length).getOrElse(0),
                scenarioCategory = scenario.processCategory,
                scenarioVersion = scenario.processVersionId,
                createdBy = scenario.createdBy,
                fragmentsUsedCount = getFragmentsUsedInScenario(scenario.scenarioGraph),
                lastDeployedAction = scenario.lastDeployedAction,
                scenarioId = scenario.processId
              )
            )
          )
        }
    }
    def fetchActivity(
        scenarioInputData: List[ScenarioStatisticsInputData]
    ): Future[Either[StatisticError, List[DbProcessActivityRepository.ProcessActivity]]] = {
      val scenarioIds = scenarioInputData.flatMap(_.scenarioId)

      scenarioIds.map(scenarioId => scenarioActivityRepository.findActivity(scenarioId)).sequence.map(Right(_))
    }

    def fetchComponentList(): Future[Either[StatisticError, List[ComponentListElement]]] = {
      componentService.getComponentsList
        .map(Right(_))
    }

    new UsageStatisticsReportsSettingsService(
      config,
      urlConfig,
      fingerprintService,
      fetchNonArchivedScenarioParameters,
      fetchActivity,
      fetchComponentList,
      () => ignoringErrorsFEStatisticsRepository.read(),
      componentList,
      designerClock
    )

  }

  private def getFragmentsUsedInScenario(scenarioGraph: Option[ScenarioGraph]): Int = {
    scenarioGraph match {
      case Some(graph) =>
        graph.nodes.map {
          case _: FragmentInput => 1
          case _                => 0
        }.sum
      case None => 0
    }
  }

}

class UsageStatisticsReportsSettingsService(
    config: UsageStatisticsReportsConfig,
    urlConfig: StatisticUrlConfig,
    fingerprintService: FingerprintService,
    fetchNonArchivedScenariosInputData: () => Future[Either[StatisticError, List[ScenarioStatisticsInputData]]],
    fetchActivity: List[ScenarioStatisticsInputData] => Future[
      Either[StatisticError, List[DbProcessActivityRepository.ProcessActivity]]
    ],
    fetchComponentList: () => Future[Either[StatisticError, List[ComponentListElement]]],
    fetchFeStatistics: () => Future[Map[String, Long]],
    components: List[ComponentDefinitionWithImplementation],
    designerClock: Clock
)(implicit ec: ExecutionContext) {
  private val statisticsUrls = new StatisticsUrls(urlConfig)

  def prepareStatisticsUrl(): Future[Either[StatisticError, List[String]]] = {
    if (config.enabled) {
      val maybeUrls = for {
        queryParams <- determineQueryParams()
        fingerprint <- new EitherT(fingerprintService.fingerprint(config, nuFingerprintFileName))
        correlationId = CorrelationId.apply()
        urls <- EitherT.pure[Future, StatisticError](
          statisticsUrls.prepare(fingerprint, correlationId, queryParams)
        )
      } yield urls
      maybeUrls.value
    } else {
      Future.successful(Right(Nil))
    }
  }

  private[statistics] def determineQueryParams(): EitherT[Future, StatisticError, Map[String, String]] = {
    for {
      scenariosInputData <- new EitherT(fetchNonArchivedScenariosInputData())
      scenariosStatistics = ScenarioStatistics.getScenarioStatistics(scenariosInputData)
      basicStatistics     = determineBasicStatistics(config)
      generalStatistics   = ScenarioStatistics.getGeneralStatistics(scenariosInputData)
      activity <- new EitherT(fetchActivity(scenariosInputData))
      activityStatistics = ScenarioStatistics.getActivityStatistics(activity)
      componentList <- new EitherT(fetchComponentList())
      componentStatistics = ScenarioStatistics.getComponentStatistic(componentList, components)
      feStatistics <- EitherT.liftF(fetchFeStatistics())
      designerUptimeStatistics = getDesignerUptimeStatistics
    } yield basicStatistics ++
      scenariosStatistics ++
      generalStatistics ++
      activityStatistics ++
      componentStatistics ++
      feStatistics.map { case (k, v) =>
        k -> v.toString
      } ++
      designerUptimeStatistics
  }

  private def determineBasicStatistics(
      config: UsageStatisticsReportsConfig
  ): Map[String, String] =
    Map(
      // If it is not set, we assume that it is some custom build from source code
      NuSource.name  -> config.source.filterNot(_.isBlank).getOrElse("sources"),
      NuVersion.name -> BuildInfo.version
    )

  private def getDesignerUptimeStatistics: Map[String, String] = {
    Map(DesignerUptime.name -> (Instant.now().getEpochSecond - designerClock.instant().getEpochSecond).toString)
  }

}

private[statistics] case class ScenarioStatisticsInputData(
    isFragment: Boolean,
    processingMode: ProcessingMode,
    deploymentManagerType: DeploymentManagerType,
    // For fragments status is empty
    status: Option[StateStatus],
    nodesCount: Int,
    scenarioCategory: String,
    scenarioVersion: VersionId,
    createdBy: String,
    fragmentsUsedCount: Int,
    lastDeployedAction: Option[ProcessAction],
    scenarioId: Option[ProcessId]
)

private[statistics] class IgnoringErrorsFEStatisticsRepository(repository: FEStatisticsRepository[Future])(
    implicit ec: ExecutionContext
) extends ReadFEStatisticsRepository[Future]
    with LazyLogging {

  override def read(): Future[Map[String, Long]] = repository
    .read()
    .recover { case ex: Exception =>
      logger.warn("Exception occurred during statistics read", ex)
      Map.empty[String, Long]
    }

}
