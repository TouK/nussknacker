package pl.touk.nussknacker.ui.statistics

import cats.data.EitherT
import cats.implicits.{toFoldableOps, toTraverseOps}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{ComponentType, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ScenarioActionName, StateStatus}
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
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer.{determineStatisticsForScenario, nuFingerprintFileName, prepareUrlString, toURL}

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
                scenario.isFragment,
                scenario.processingMode,
                deploymentManagerTypeByProcessingType(scenario.processingType),
                scenario.state.map(_.status),
                scenario.scenarioGraph.map(_.nodes.length).getOrElse(0),
                scenario.processCategory,
                scenario.processVersionId,
                scenario.createdBy,
                scenario.scenarioGraph
                  .map { graph =>
                    graph.nodes.map {
                      case _: FragmentInput => 1
                      case _                => 0
                    }.sum
                  }
                  .getOrElse(0)
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
        activity <- scenarioDetails.map { scenario =>
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
    new UsageStatisticsReportsSettingsDeterminer(config,
      fingerprintService,
      fetchNonArchivedScenarioParameters,
      fetchActivity,
      fetchComponentList,
      fetchScenarioActions)

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
      // Nodes stats
      nodes = scenariosInputData.map(_.nodes)
      nodesMedian  = nodes.get(nodes.length / 2).getOrElse(0)
      nodesAverage = nodes.sum / scenariosInputData.length
      // Category stats
      categories = scenariosInputData.map(_.processCategory).toSet.size
      //        Version stats
      versions        = scenariosInputData.map(_.processVersion.value).sorted
      versionsMedian  = versions.get(versions.length / 2).getOrElse(-1)
      versionsAverage = versions.sum / scenariosInputData.length
      //        Author stats
      authors = scenariosInputData.map(_.createdBy).toSet.size
      //        Fragment stats
      fragments        = scenariosInputData.map(_.fragmentsUsed).sorted
      fragmentsMedian  = fragments.get(fragments.length / 2).getOrElse(-1)
      fragmentsAverage = fragments.sum / scenariosStatistics("s_s").toInt
      map = Map(
        "m_n" -> nodesMedian,
        "v_n" -> nodesAverage,
        "c"   -> categories,
        "v_m" -> versionsMedian,
        "v_v" -> versionsAverage,
        "a_n" -> authors,
        "m_f" -> fragmentsMedian,
        "v_f" -> fragmentsAverage,
      ).mapValuesNow(_.toString)
      listOfActivities <- new EitherT(fetchActivity())
      //        Attachment stats
      attachments = listOfActivities.map(_.attachments).map(_.length).sorted
      attachmentMedian  = attachments.get(attachments.length / 2).getOrElse(-1)
      attachmentAverage = attachments.sum / attachments.length
      attachmentExist   = attachments.count(_ > 0)
      //        Comment stats
      comments = listOfActivities.map(_.comments).map(_.length).sorted
      commentsMedian  = comments.get(comments.length / 2).getOrElse(-1)
      commentsAverage = comments.sum / comments.length
      activityMap = Map(
        "m_a" -> attachmentMedian,
        "v_a" -> attachmentAverage,
        "e_a" -> attachmentExist,
        "m_c" -> commentsMedian,
        "v_c" -> commentsAverage
      ).mapValuesNow(_.toString)
      componentList <- new EitherT(fetchComponentList())
      withoutFragments = componentList.filterNot(comp => comp.componentType == ComponentType.Fragment)
      sortedList       = withoutFragments.sortBy(_.usageCount)(Ordering[Long].reverse)
      componentMap = Map(
        "c_u" -> sortedList.head.id.value,
        "c_n" -> sortedList.length.toString
      )
      listForScenario <- new EitherT(fetchScenarioActions())
      uptimes = listForScenario.map { listOfActions =>
        val durationsOfDeployment = calculateUpTimeStats(listOfActions).filter(_.isDefined).sequence.get.sorted
        //         return average running time for this scenario
        if (durationsOfDeployment.nonEmpty) {
          durationsOfDeployment.sum / durationsOfDeployment.length
        } else {
          0
        }
      }
      uptimeAverage = uptimes.sum / uptimes.length
      uptimeMedian  = uptimes.get(uptimes.length / 2).get
      uptimeMap = Map(
        "m_u" -> uptimeMedian,
        "v_u" -> uptimeAverage
        ).mapValuesNow(_.toString)
    } yield basicStatistics ++ scenariosStatistics ++ map ++ activityMap ++ componentMap ++ uptimeMap
  }

  private def calculateUpTimeStats(processActions: List[ProcessAction]): List[Option[Long]] = {
    val iter                    = processActions.reverseIterator
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
      listOfUptime.appended(current)
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
    nodes: Int,
    processCategory: String,
    processVersion: VersionId,
    createdBy: String,
    fragmentsUsed: Int
)
