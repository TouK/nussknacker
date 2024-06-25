package pl.touk.nussknacker.ui.statistics

import cats.data.EitherT
import cats.implicits.toTraverseOps
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentId, ComponentId, ComponentType, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, StateStatus}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.graph.node
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

import java.time.Clock
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
            scenariosDetails.map(scenario => {
              ScenarioStatisticsInputData(
                isFragment = scenario.isFragment,
                processingMode = scenario.processingMode,
                deploymentManagerType = deploymentManagerTypeByProcessingType(scenario.processingType),
                status = scenario.state.map(_.status),
                nodesCount = scenario.scenarioGraph.map(_.nodes.length).getOrElse(0),
                scenarioCategory = scenario.processCategory,
                scenarioVersion = scenario.processVersionId,
                createdBy = scenario.createdBy,
                componentsAndFragmentsUsedCount = getComponentsUsedInScenario(scenario.scenarioGraph),
                lastDeployedAction = scenario.lastDeployedAction,
                scenarioId = scenario.processId
              )
            })
          )
        }
    }
    def fetchActivity(
        scenarioInputData: List[ScenarioStatisticsInputData]
    ): Future[Either[StatisticError, List[DbProcessActivityRepository.ProcessActivity]]] = {
      val scenarioIds = scenarioInputData.flatMap(_.scenarioId)

      scenarioIds.map(scenarioId => scenarioActivityRepository.findActivity(scenarioId)).sequence.map(Right(_))
    }

    new UsageStatisticsReportsSettingsService(
      config,
      urlConfig,
      fingerprintService,
      fetchNonArchivedScenarioParameters,
      fetchActivity,
      () => ignoringErrorsFEStatisticsRepository.read(),
      componentList,
      designerClock
    )

  }

  private def getComponentsUsedInScenario(scenarioGraph: Option[ScenarioGraph]): Map[ComponentId, Int] = {
    scenarioGraph match {
      case Some(graph) =>
        graph.nodes
          .map {
            case data: node.CustomNodeData           => ComponentId(ComponentType.CustomComponent, data.componentId)
            case data: node.Source                   => ComponentId(ComponentType.Source, data.componentId)
            case data: node.Sink                     => ComponentId(ComponentType.Sink, data.componentId)
            case data: node.VariableBuilder          => BuiltInComponentId.RecordVariable
            case data: node.Variable                 => BuiltInComponentId.Variable
            case data: node.Enricher                 => ComponentId(ComponentType.Service, data.componentId)
            case data: node.Processor                => ComponentId(ComponentType.Service, data.componentId)
            case data: FragmentInput                 => ComponentId(ComponentType.Fragment, "fragment")
            case data: node.FragmentUsageOutput      => ComponentId(ComponentType.Fragment, "fragment")
            case data: node.Filter                   => BuiltInComponentId.Filter
            case data: node.Switch                   => BuiltInComponentId.Choice
            case data: node.Split                    => BuiltInComponentId.Split
            case data: node.FragmentInputDefinition  => BuiltInComponentId.FragmentInputDefinition
            case data: node.FragmentOutputDefinition => BuiltInComponentId.FragmentOutputDefinition
            case data: node.BranchEndData            => ComponentId(ComponentType.Sink, data.id)
          }
          .filterNot(comp => comp.`type` == ComponentType.BuiltIn && (comp.name == "input" || comp.name == "output"))
          .groupBy(identity)
          .map { case (id, list) => (id, list.size) }
      case None => Map.empty
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
    fetchFeStatistics: () => Future[Map[String, Long]],
    components: List[ComponentDefinitionWithImplementation],
    designerClock: Clock
)(implicit ec: ExecutionContext) {
  private val statisticsUrls    = new StatisticsUrls(urlConfig)
  private val designerStartTime = designerClock.instant()

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
      generalStatistics   = ScenarioStatistics.getGeneralStatistics(scenariosInputData, components)
      activity <- new EitherT(fetchActivity(scenariosInputData))
      activityStatistics = ScenarioStatistics.getActivityStatistics(activity)
//      componentList <- new EitherT(fetchComponentList())
//      componentStatistics = ScenarioStatistics.getComponentStatistic(componentList, components)
      feStatistics <- EitherT.liftF(fetchFeStatistics())
      designerUptimeStatistics = getDesignerUptimeStatistics
    } yield basicStatistics ++
      scenariosStatistics ++
      generalStatistics ++
      activityStatistics ++
//      componentStatistics ++
      designerUptimeStatistics ++
      feStatistics.map { case (k, v) =>
        k -> v.toString
      }
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
    componentsAndFragmentsUsedCount: Map[ComponentId, Int],
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
