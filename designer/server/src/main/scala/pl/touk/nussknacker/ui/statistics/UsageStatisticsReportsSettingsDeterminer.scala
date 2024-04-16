package pl.touk.nussknacker.ui.statistics

import cats.data.EitherT
import cats.implicits.{toFoldableOps, toTraverseOps}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{ComponentType, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ScenarioActionName, StateStatus}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.graph.node.FragmentInput
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.restmodel.component.ComponentListElement
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.definition.component.ComponentService
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.processingtype.{DeploymentManagerType, ProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.process.repository.{DbProcessActivityRepository, ProcessActivityRepository}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.{LoggedUser, NussknackerInternalUser}
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer._
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer.{determineStatisticsForScenario, nuFingerprintFileName, prepareUrlString, toURL}
import shapeless.syntax.std.tuple.productTupleOps

import java.net.{URI, URL, URLEncoder}
import java.nio.charset.StandardCharsets
import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object UsageStatisticsReportsSettingsDeterminer extends LazyLogging {

  private val nuFingerprintFileName = new FileName("nussknacker.fingerprint")

  private val flinkDeploymentManagerType = DeploymentManagerType("flinkStreaming")

  private val liteK8sDeploymentManagerType = DeploymentManagerType("lite-k8s")

  private val liteEmbeddedDeploymentManagerType = DeploymentManagerType("lite-embedded")

  private val knownDeploymentManagerTypes =
    Set(flinkDeploymentManagerType, liteK8sDeploymentManagerType, liteEmbeddedDeploymentManagerType)

  def apply(
      config: UsageStatisticsReportsConfig,
      processService: ProcessService,
      // TODO: Instead of passing deploymentManagerTypes next to processService, we should split domain ScenarioWithDetails from DTOs - see comment in ScenarioWithDetails
      deploymentManagerTypes: ProcessingTypeDataProvider[DeploymentManagerType, _],
      fingerprintService: FingerprintService,
      scenarioActivityRepository: ProcessActivityRepository,
      componentService: ComponentService
  )(implicit ec: ExecutionContext): UsageStatisticsReportsSettingsDeterminer = {
    def fetchNonArchivedScenarioParameters(): Future[Either[StatisticError, List[ScenarioStatisticsInputData]]] = {
      // TODO: Warning, here is a security leak. We report statistics in the scope of processing types to which
      //       given user has no access rights.
      val user                                  = NussknackerInternalUser.instance
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
              fragmentsUsedCount = getFragmentsUsedInScenario(scenario.scenarioGraph)
              )
            )
          )
        }
    }

    def fetchActivity(): Future[Either[StatisticError, List[DbProcessActivityRepository.ProcessActivity]]] = {
      implicit val user: LoggedUser = NussknackerInternalUser.instance
      for {
        scenarioDetails <- processService.getLatestProcessesWithDetails(
          ScenarioQuery.unarchived,
          GetScenarioWithDetailsOptions.detailsOnly.withFetchState
        )
        activity <- scenarioDetails.collect {
          case scenario if scenario.processId.isDefined =>
            scenarioActivityRepository.findActivity(scenario.processIdUnsafe)
        }.sequence
      } yield Right(activity)
    }

    def fetchComponentList(): Future[Either[StatisticError, List[ComponentListElement]]] = {
      implicit val user: LoggedUser = NussknackerInternalUser.instance
      componentService
        .getComponentsList
        .map(Right(_))
    }

    def fetchScenarioActions(): Future[Either[StatisticError, List[List[ProcessAction]]]] = {
      implicit val user: LoggedUser = NussknackerInternalUser.instance
      processService
        .getLatestProcessesWithDetails(
          ScenarioQuery.unarchivedProcesses,
          GetScenarioWithDetailsOptions.detailsOnly.withFetchState
        )(user)
        .map(scenarioList =>
          scenarioList.map { scenario =>
            processService.getProcessActions(scenario.processIdUnsafe)
          }.sequence
        )
        .flatten
        .map(Right(_))
    }
    new UsageStatisticsReportsSettingsDeterminer(
      config,
      fingerprintService,
      fetchNonArchivedScenarioParameters,
      fetchActivity,
      fetchComponentList,
      fetchScenarioActions
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

  // We have four dimensions:
  // - scenario / fragment
  // - processing mode: streaming, r-r, batch
  // - dm type: flink, k8s, embedded, custom
  // - status: active (running), other
  // We have two options:
  // 1. To aggregate statistics for every combination - it give us 3*4*2 + 3*4 = 36 parameters
  // 2. To aggregate statistics for every dimension separately - it gives us 2+3+4+1 = 10 parameters
  // We decided to pick the 2nd option which gives a reasonable balance between amount of collected data and insights
  private[statistics] def determineStatisticsForScenario(inputData: ScenarioStatisticsInputData): Map[String, Int] = {
    Map(
      "s_s"     -> !inputData.isFragment,
      "s_f"     -> inputData.isFragment,
      "s_pm_s"  -> (inputData.processingMode == ProcessingMode.UnboundedStream),
      "s_pm_b"  -> (inputData.processingMode == ProcessingMode.BoundedStream),
      "s_pm_rr" -> (inputData.processingMode == ProcessingMode.RequestResponse),
      "s_dm_f"  -> (inputData.deploymentManagerType == flinkDeploymentManagerType),
      "s_dm_l"  -> (inputData.deploymentManagerType == liteK8sDeploymentManagerType),
      "s_dm_e"  -> (inputData.deploymentManagerType == liteEmbeddedDeploymentManagerType),
      "s_dm_c"  -> !knownDeploymentManagerTypes.contains(inputData.deploymentManagerType),
      "s_a"     -> inputData.status.contains(SimpleStateStatus.Running),
    ).mapValuesNow(if (_) 1 else 0)
  }

  private[statistics] def prepareUrlString(queryParams: Map[String, String]): String = {
    // Sorting for purpose of easier testing
    queryParams.toList
      .sortBy(_._1)
      .map { case (k, v) =>
        s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
      }
      .mkString("https://stats.nussknacker.io/?", "&", "")
  }

  private def toURL(urlString: String): Either[StatisticError, Option[URL]] =
    Try(new URI(urlString).toURL) match {
      case Failure(ex) => {
        logger.warn(s"Exception occurred while creating URL from string: [$urlString]", ex)
        Left(CannotGenerateStatisticsError)
      }
      case Success(value) => Right(Some(value))
    }

}

class UsageStatisticsReportsSettingsDeterminer(
    config: UsageStatisticsReportsConfig,
    fingerprintService: FingerprintService,
    fetchNonArchivedScenariosInputData: () => Future[Either[StatisticError, List[ScenarioStatisticsInputData]]],
    fetchActivity: () => Future[Either[StatisticError, List[DbProcessActivityRepository.ProcessActivity]]],
    fetchComponentList: () => Future[Either[StatisticError, List[ComponentListElement]]],
    fetchScenarioActions: () => Future[Either[StatisticError, List[List[ProcessAction]]]]
)(implicit ec: ExecutionContext) {

  def prepareStatisticsUrl(): Future[Either[StatisticError, Option[URL]]] = {
    if (config.enabled) {
      determineQueryParams().value
        .map {
          case Right(queryParams) => toURL(prepareUrlString(queryParams))
          case Left(e)            => Left(e)
        }
    } else {
      Future.successful(Right(None))
    }
  }

  private[statistics] def determineQueryParams(): EitherT[Future, StatisticError, Map[String, String]] = {
    for {
      scenariosInputData <- new EitherT(fetchNonArchivedScenariosInputData())
      scenariosStatistics = scenariosInputData.map(determineStatisticsForScenario).combineAll.mapValuesNow(_.toString)
      fingerprint <- new EitherT(fingerprintService.fingerprint(config, nuFingerprintFileName))
      basicStatistics = determineBasicStatistics(fingerprint, config)
      scenariosCount = scenariosInputData.count(!_.isFragment)
      // Nodes stats
      sortedNodes = scenariosInputData.map(_.nodesCount)
      nodesMedian  = sortedNodes.get(sortedNodes.length / 2).getOrElse(0)
      nodesAverage = sortedNodes.sum / scenariosInputData.length
      nodesMax     = sortedNodes.head
      nodesMin     = sortedNodes.last
      // Category stats
      categoriesCount = scenariosInputData.map(_.scenarioCategory).toSet.size
      //        Version stats
      sortedVersions  = scenariosInputData.map(_.scenarioVersion.value).sorted
      versionsMedian  = sortedVersions.get(sortedVersions.length / 2).getOrElse(-1)
      versionsAverage = sortedVersions.sum / scenariosInputData.length
      versionsMax     = sortedVersions.head
      versionsMin     = sortedVersions.last
      //        Author stats
      authorsCount = scenariosInputData.map(_.createdBy).toSet.size
      //        Fragment stats
      fragments        = scenariosInputData.map(_.fragmentsUsedCount).sorted
      fragmentsMedian  = fragments.get(fragments.length / 2).getOrElse(0)
      fragmentsAverage = fragments.sum / scenariosCount
      map = Map(
        NodesMedian      -> nodesMedian,
        NodesAverage     -> nodesAverage,
        NodesMax         -> nodesMax,
        NodesMin         -> nodesMin,
        CategoriesCount  -> categoriesCount,
        VersionsMedian   -> versionsMedian,
        VersionsAverage  -> versionsAverage,
        VersionsMax      -> versionsMax,
        VersionsMin      -> versionsMin,
        AuthorsCount     -> authorsCount,
        FragmentsMedian  -> fragmentsMedian,
        FragmentsAverage -> fragmentsAverage,
      ).map { case (k, v) => (k.toString, v.toString) }
      listOfActivities <- new EitherT(fetchActivity())
      //        Attachment stats
      sortedAttachmentCountList = listOfActivities.map(_.attachments).map(_.length).sorted
      attachmentAverage = sortedAttachmentCountList.sum / sortedAttachmentCountList.length
      attachmentsTotal  = sortedAttachmentCountList.sum
      //        Comment stats
      comments = listOfActivities.map(_.comments).map(_.length).sorted
      commentsTotal   = comments.sum
      commentsAverage = comments.sum / comments.length
      activityMap = Map(
        AttachmentsAverage -> attachmentAverage,
        AttachmentsTotal   -> attachmentsTotal,
        CommentsTotal      -> commentsTotal,
        CommentsAverage    -> commentsAverage
      ).map { case (k, v) => (k.toString, v.toString) }
      componentList <- new EitherT(fetchComponentList())
      withoutFragments      = componentList.filterNot(comp => comp.componentType == ComponentType.Fragment)
      componentsByNameCount = withoutFragments.groupBy(_.name)
      componentMap = Map(
        ComponentsCount -> componentsByNameCount.size
      ).map { case (k, v) => (k.toString, v.toString) }
      listForScenarios <- new EitherT(fetchScenarioActions())
      uptimeMap <- listForScenarios.map {
        case List() => Map.empty[String, String]
        case listForScenarios =>
          val uptimes = listForScenarios.map { listOfActions =>
            calculateUpTimeStats(listOfActions).flatten.sorted match {
              case Nil                    => 0
              case deploymentsUpTimeStats => deploymentsUpTimeStats.sum.toInt / deploymentsUpTimeStats.length
            }
          }
          val uptimeAverage = uptimes.sum / uptimes.length
          val uptimeMax     = uptimes.head
          val uptimeMin     = uptimes.last
          Map(
            UptimeAverage -> uptimeAverage,
            UptimeMax     -> uptimeMax,
            UptimeMin     -> uptimeMin
          ).map { case (k, v) => (k.toString, v.toString) }
      }
    } yield basicStatistics ++ scenariosStatistics ++ map ++ activityMap ++ componentMap ++ uptimeMap
  }

  private def calculateUpTimeStats(scenarioActions: List[ProcessAction]): List[Option[Long]] = {
    val iter                    = scenarioActions.reverseIterator
    var hasStarted              = false
    var lastActionTime: Instant = Instant.now
    val listOfUptime = iter.map {
      case ProcessAction(_, _, _, _, _, performedAt, ScenarioActionName.Deploy, _, _, _, _, _) =>
        if (hasStarted) {
          val uptime = performedAt.getEpochSecond - lastActionTime.getEpochSecond
          hasStarted = true
          lastActionTime = performedAt
          Some(uptime)
        } else {
          hasStarted = true
          lastActionTime = performedAt
          None
        }
      case ProcessAction(_, _, _, _, _, performedAt, ScenarioActionName.Cancel, _, _, _, _, _) =>
        val uptime = performedAt.getEpochSecond - lastActionTime.getEpochSecond
        hasStarted = false
        lastActionTime = performedAt
        Some(uptime)
      case _ =>
        None
    }.toList
    //    if last action is deploy add time for ongoing scenario
    if (hasStarted) {
      val current: Option[Long] = Some(Instant.now.getEpochSecond - lastActionTime.getEpochSecond)
      listOfUptime :+ current
    } else {
      listOfUptime
    }
  }

  private def determineBasicStatistics(
      fingerprint: Fingerprint,
      config: UsageStatisticsReportsConfig
  ): Map[String, String] =
    Map(
      "fingerprint" -> fingerprint.value,
      // If it is not set, we assume that it is some custom build from source code
      "source"  -> config.source.filterNot(_.isBlank).getOrElse("sources"),
      "version" -> BuildInfo.version
    )
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
    fragmentsUsedCount: Int
)

sealed abstract class StatisticKey(val name: String) {
  override def toString: String = name
}

case object AuthorsCount       extends StatisticKey("a_n")
case object CategoriesCount    extends StatisticKey("c")
case object ComponentsCount    extends StatisticKey("c_n")
case object VersionsMedian     extends StatisticKey("v_m")
case object AttachmentsTotal   extends StatisticKey("a_t")
case object AttachmentsAverage extends StatisticKey("a_v")
case object VersionsMax        extends StatisticKey("v_ma")
case object VersionsMin        extends StatisticKey("v_mi")
case object VersionsAverage    extends StatisticKey("v_v")
case object UptimeAverage      extends StatisticKey("u_v")
case object UptimeMax          extends StatisticKey("u_ma")
case object UptimeMin          extends StatisticKey("u_mi")
case object CommentsAverage    extends StatisticKey("c_v")
case object CommentsTotal      extends StatisticKey("c_t")
case object FragmentsMedian    extends StatisticKey("f_m")
case object FragmentsAverage   extends StatisticKey("f_v")
case object NodesMedian        extends StatisticKey("n_m")
case object NodesAverage       extends StatisticKey("n_v")
case object NodesMax           extends StatisticKey("n_ma")
case object NodesMin           extends StatisticKey("n_mi")
