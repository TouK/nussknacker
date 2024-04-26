package pl.touk.nussknacker.ui.statistics

import cats.implicits.toFoldableOps
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.ui.config.UsageStatisticsReportsConfig
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.processingtype.{DeploymentManagerType, ProcessingTypeDataProvider}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioQuery}
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser
import pl.touk.nussknacker.ui.statistics.UsageStatisticsReportsSettingsDeterminer._

import java.net.{URI, URL, URLEncoder}
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object UsageStatisticsReportsSettingsDeterminer {

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
      fingerprintSupplier: (UsageStatisticsReportsConfig, FileName) => Future[Fingerprint]
  )(implicit ec: ExecutionContext): UsageStatisticsReportsSettingsDeterminer = {
    def fetchNonArchivedScenarioParameters(): Future[List[ScenarioStatisticsInputData]] = {
      // TODO: Warning, here is a security leak. We report statistics in the scope of processing types to which
      //       given user has no access rights.
      val user                                  = NussknackerInternalUser.instance
      val deploymentManagerTypeByProcessingType = deploymentManagerTypes.all(user)
      processService
        .getLatestProcessesWithDetails(
          ScenarioQuery.unarchived,
          GetScenarioWithDetailsOptions.detailsOnly.withFetchState
        )(user)
        .map { scenariosDetails =>
          scenariosDetails.map(scenario =>
            ScenarioStatisticsInputData(
              scenario.isFragment,
              scenario.processingMode,
              deploymentManagerTypeByProcessingType(scenario.processingType),
              scenario.state.map(_.status)
            )
          )
        }
    }
    new UsageStatisticsReportsSettingsDeterminer(config, fingerprintSupplier, fetchNonArchivedScenarioParameters)
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

  private[statistics] def prepareUrl(queryParams: Map[String, String]) = {
    // Sorting for purpose of easier testing
    queryParams.toList
      .sortBy(_._1)
      .map { case (k, v) =>
        s"${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
      }
      .mkString("https://stats.nussknacker.io/?", "&", "")
  }

  private def toURL(urlString: String): Future[Option[URL]] =
    Try(new URI(urlString).toURL) match {
      case Failure(ex)    => Future.failed(new IllegalStateException("Invalid URL generated", ex))
      case Success(value) => Future.successful(Some(value))
    }

}

class UsageStatisticsReportsSettingsDeterminer(
    config: UsageStatisticsReportsConfig,
    fingerprintSupplier: (UsageStatisticsReportsConfig, FileName) => Future[Fingerprint],
    fetchNonArchivedScenariosInputData: () => Future[List[ScenarioStatisticsInputData]]
)(implicit ec: ExecutionContext) {

  def determineStatisticsUrl(): Future[Option[URL]] = {
    if (config.enabled) {
      determineQueryParams()
        .flatMap { queryParams =>
          val url = prepareUrl(queryParams)
          toURL(url)
        }
    } else {
      Future.successful(None)
    }
  }

  private[statistics] def determineQueryParams(): Future[Map[String, String]] =
    for {
      scenariosInputData <- fetchNonArchivedScenariosInputData()
      scenariosStatistics = scenariosInputData.map(determineStatisticsForScenario).combineAll.mapValuesNow(_.toString)
      fingerprint <- fingerprintSupplier(config, nuFingerprintFileName)
      basicStatistics = determineBasicStatistics(fingerprint, config)
    } yield basicStatistics ++ scenariosStatistics

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
    status: Option[StateStatus]
)
