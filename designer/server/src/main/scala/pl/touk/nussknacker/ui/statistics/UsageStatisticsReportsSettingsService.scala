package pl.touk.nussknacker.ui.statistics

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{DesignerWideComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, StateStatus}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.graph.node.FragmentInput
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.db.timeseries.{FEStatisticsRepository, ReadFEStatisticsRepository}
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.processingtype.{DeploymentManagerType, ProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}

import java.time.Clock
import scala.concurrent.{ExecutionContext, Future}

object UsageStatisticsReportsSettingsService extends LazyLogging {

  def apply(
      config: UsageStatisticsReportsConfig,
      processService: ProcessService,
      // TODO: Instead of passing deploymentManagerTypes next to processService, we should split domain ScenarioWithDetails from DTOs - see comment in ScenarioWithDetails
      deploymentManagerTypes: ProcessingTypeDataProvider[DeploymentManagerType, _],
      fingerprintService: FingerprintService,
      scenarioActivityRepository: ScenarioActivityRepository,
      componentService: ComponentService,
      statisticsRepository: FEStatisticsRepository[Future],
      componentList: List[ComponentDefinitionWithImplementation],
      designerClock: Clock,
      dbioRunner: DBIOActionRunner,
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
    def fetchActivity(): Future[Map[String, Int]] = dbioRunner.run {
      scenarioActivityRepository.getActivityStats
    }

    def fetchComponentUsage(): Future[Map[DesignerWideComponentId, Long]] = {
      componentService.getUsagesPerDesignerWideComponentId
    }

    new UsageStatisticsReportsSettingsService(
      config,
      fingerprintService,
      fetchNonArchivedScenarioParameters,
      fetchActivity,
      () => ignoringErrorsFEStatisticsRepository.read(),
      componentList,
      fetchComponentUsage,
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
    fingerprintService: FingerprintService,
    fetchNonArchivedScenariosInputData: () => Future[Either[StatisticError, List[ScenarioStatisticsInputData]]],
    fetchActivity: () => Future[Map[String, Int]],
    fetchFeStatistics: () => Future[RawFEStatistics],
    components: List[ComponentDefinitionWithImplementation],
    componentUsage: () => Future[Map[DesignerWideComponentId, Long]],
    designerClock: Clock
)(implicit ec: ExecutionContext) {
  private val designerStartTime = designerClock.instant()

  def prepareStatisticsUrl(): Future[Either[StatisticError, Statistics]] = {
    if (config.enabled) {
      determineStatistics().value
    } else {
      Future.successful(Right(Statistics.Empty))
    }
  }

  private def determineStatistics(): EitherT[Future, StatisticError, Statistics] = {
    for {
      scenariosInputData <- new EitherT(fetchNonArchivedScenariosInputData())
      scenariosStatistics = ScenarioStatistics.getScenarioStatistics(scenariosInputData)
      basicStatistics     = determineBasicStatistics(config)
      generalStatistics   = ScenarioStatistics.getGeneralStatistics(scenariosInputData)
      attachmentsAndCommentsTotal <- EitherT.liftF(fetchActivity())
      activityStatistics = ScenarioStatistics.getActivityStatistics(
        attachmentsAndCommentsTotal,
        scenariosInputData.length
      )
      componentDesignerWideUsage <- EitherT.liftF(componentUsage())
      componentStatistics = ScenarioStatistics.getComponentStatistics(componentDesignerWideUsage, components)
      feStatistics <- EitherT.liftF(fetchFeStatistics())
      designerUptimeStatistics = getDesignerUptimeStatistics
      fingerprint <- new EitherT(fingerprintService.fingerprint(config))
      requestId = RequestId()
      combinedStatistics = basicStatistics ++
        scenariosStatistics ++
        generalStatistics ++
        activityStatistics ++
        componentStatistics ++
        designerUptimeStatistics ++
        feStatistics.raw.map { case (k, v) =>
          k -> v.toString
        }
    } yield new Statistics.NonEmpty(fingerprint, requestId, combinedStatistics)
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
    Map(
      DesignerUptimeInSeconds.name -> (designerClock
        .instant()
        .getEpochSecond - designerStartTime.getEpochSecond).toString
    )
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

  override def read(): Future[RawFEStatistics] = repository
    .read()
    .recover { case ex: Exception =>
      logger.warn("Exception occurred during statistics read", ex)
      RawFEStatistics.empty
    }

}
