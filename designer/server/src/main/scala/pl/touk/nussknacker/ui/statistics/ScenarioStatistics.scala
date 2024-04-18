package pl.touk.nussknacker.ui.statistics

import cats.implicits.toFoldableOps
import pl.touk.nussknacker.engine.api.component.{ComponentType, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.restmodel.component
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository

import java.time.Instant

object ScenarioStatistics {

  private val flinkDeploymentManagerType = DeploymentManagerType("flinkStreaming")

  private val liteK8sDeploymentManagerType = DeploymentManagerType("lite-k8s")

  private val liteEmbeddedDeploymentManagerType = DeploymentManagerType("lite-embedded")

  private val knownDeploymentManagerTypes =
    Set(flinkDeploymentManagerType, liteK8sDeploymentManagerType, liteEmbeddedDeploymentManagerType)

  def getGeneralStatistics(scenariosInputData: List[ScenarioStatisticsInputData]): Map[String, String] = {
    if (scenariosInputData.isEmpty) {
      Map.empty
    } else {
      val scenariosCount = scenariosInputData.count(!_.isFragment)
      //        Nodes stats
      val sortedNodes  = scenariosInputData.map(_.nodesCount).sorted
      val nodesMedian  = calculateMedian(sortedNodes)
      val nodesAverage = sortedNodes.sum / scenariosInputData.length
      val nodesMax     = sortedNodes.head
      val nodesMin     = sortedNodes.last
      //        Category stats
      val categoriesCount = scenariosInputData.map(_.scenarioCategory).toSet.size
      //        Version stats
      val sortedVersions  = scenariosInputData.map(_.scenarioVersion.value).sorted
      val versionsMedian  = calculateMedian(sortedVersions)
      val versionsAverage = sortedVersions.sum / scenariosInputData.length
      val versionsMax     = sortedVersions.head
      val versionsMin     = sortedVersions.last
      //        Author stats
      val authorsCount = scenariosInputData.map(_.createdBy).toSet.size
      //        Fragment stats
      val fragmentsCount   = scenariosInputData.map(_.fragmentsUsedCount).sorted
      val fragmentsMedian  = calculateMedian(fragmentsCount)
      val fragmentsAverage = fragmentsCount.sum / scenariosCount
      //          Uptime stats
      val lastActions = scenariosInputData.flatMap(_.lastDeployedAction)
      val sortedUptimes = lastActions.map { action =>
        Instant.now.getEpochSecond - action.performedAt.getEpochSecond
      }.sorted
      val uptimeStatsMap = {
        if (sortedUptimes.isEmpty) {
          Map(
            UptimeAverage -> 0,
            UptimeMax     -> 0,
            UptimeMin     -> 0,
          )
        } else {
          Map(
            UptimeAverage -> sortedUptimes.sum / sortedUptimes.length,
            UptimeMax     -> sortedUptimes.head,
            UptimeMin     -> sortedUptimes.last
          )
        }
      }

      (Map(
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
        FragmentsAverage -> fragmentsAverage
      ) ++ uptimeStatsMap)
        .map { case (k, v) => (k.toString, v.toString) }
    }
  }

  def getActivityStatistics(
      listOfActivities: List[DbProcessActivityRepository.ProcessActivity]
  ): Map[String, String] = {
    if (listOfActivities.isEmpty) {
      Map.empty
    } else {
      //        Attachment stats
      val sortedAttachmentCountList = listOfActivities.map(_.attachments.length).sorted
      val attachmentAverage         = sortedAttachmentCountList.sum / sortedAttachmentCountList.length
      val attachmentsTotal          = sortedAttachmentCountList.sum
      //        Comment stats
      val comments        = listOfActivities.map(_.comments.length).sorted
      val commentsTotal   = comments.sum
      val commentsAverage = comments.sum / comments.length

      Map(
        AttachmentsAverage -> attachmentAverage,
        AttachmentsTotal   -> attachmentsTotal,
        CommentsTotal      -> commentsTotal,
        CommentsAverage    -> commentsAverage
      ).map { case (k, v) => (k.toString, v.toString) }
    }
  }

  def getComponentStatistic(componentList: List[component.ComponentListElement]): Map[String, String] = {
    if (componentList.isEmpty) {
      Map.empty
    } else {
      val withoutFragments      = componentList.filterNot(comp => comp.componentType == ComponentType.Fragment)
      val componentsByNameCount = withoutFragments.groupBy(_.name).size

      Map(
        ComponentsCount -> componentsByNameCount
      ).map { case (k, v) => (k.toString, v.toString) }
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
      ScenarioCount        -> !inputData.isFragment,
      FragmentCount        -> inputData.isFragment,
      UnboundedStreamCount -> (inputData.processingMode == ProcessingMode.UnboundedStream),
      BoundedStreamCount   -> (inputData.processingMode == ProcessingMode.BoundedStream),
      RequestResponseCount -> (inputData.processingMode == ProcessingMode.RequestResponse),
      FlinkDMCount         -> (inputData.deploymentManagerType == flinkDeploymentManagerType),
      LiteK8sDMCount       -> (inputData.deploymentManagerType == liteK8sDeploymentManagerType),
      LiteEmbeddedDMCount  -> (inputData.deploymentManagerType == liteEmbeddedDeploymentManagerType),
      UnknownDMCount       -> !knownDeploymentManagerTypes.contains(inputData.deploymentManagerType),
      ActiveCount          -> inputData.status.contains(SimpleStateStatus.Running),
    ).map { case (k, v) => (k.toString, if (v) 1 else 0) }
  }

  private def calculateMedian[T: Numeric](list: List[T]): T = {
    list.get(list.size / 2).getOrElse(implicitly[Numeric[T]].zero)
  }

}

sealed abstract class StatisticKey(val name: String) {
  override def toString: String = name
}

case object AuthorsCount         extends StatisticKey("a_n")
case object CategoriesCount      extends StatisticKey("c")
case object ComponentsCount      extends StatisticKey("c_n")
case object VersionsMedian       extends StatisticKey("v_m")
case object AttachmentsTotal     extends StatisticKey("a_t")
case object AttachmentsAverage   extends StatisticKey("a_v")
case object VersionsMax          extends StatisticKey("v_ma")
case object VersionsMin          extends StatisticKey("v_mi")
case object VersionsAverage      extends StatisticKey("v_v")
case object UptimeAverage        extends StatisticKey("u_v")
case object UptimeMax            extends StatisticKey("u_ma")
case object UptimeMin            extends StatisticKey("u_mi")
case object CommentsAverage      extends StatisticKey("c_v")
case object CommentsTotal        extends StatisticKey("c_t")
case object FragmentsMedian      extends StatisticKey("f_m")
case object FragmentsAverage     extends StatisticKey("f_v")
case object NodesMedian          extends StatisticKey("n_m")
case object NodesAverage         extends StatisticKey("n_v")
case object NodesMax             extends StatisticKey("n_ma")
case object NodesMin             extends StatisticKey("n_mi")
case object ScenarioCount        extends StatisticKey("s_s")
case object FragmentCount        extends StatisticKey("s_f")
case object UnboundedStreamCount extends StatisticKey("s_pm_s")
case object BoundedStreamCount   extends StatisticKey("s_pm_b")
case object RequestResponseCount extends StatisticKey("s_pm_rr")
case object FlinkDMCount         extends StatisticKey("s_dm_f")
case object LiteK8sDMCount       extends StatisticKey("s_dm_l")
case object LiteEmbeddedDMCount  extends StatisticKey("s_dm_e")
case object UnknownDMCount       extends StatisticKey("s_dm_c")
case object ActiveCount          extends StatisticKey("s_a")
