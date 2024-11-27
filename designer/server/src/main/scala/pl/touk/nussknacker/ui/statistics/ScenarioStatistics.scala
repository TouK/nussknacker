package pl.touk.nussknacker.ui.statistics

import cats.implicits.toFoldableOps
import pl.touk.nussknacker.engine.api.component.{DesignerWideComponentId, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType

import java.time.Instant

object ScenarioStatistics {

  private val flinkDeploymentManagerType = DeploymentManagerType("flinkStreaming")

  private val liteK8sDeploymentManagerType = DeploymentManagerType("lite-k8s")

  private val liteEmbeddedDeploymentManagerType = DeploymentManagerType("lite-embedded")

  private val knownDeploymentManagerTypes =
    Set(flinkDeploymentManagerType, liteK8sDeploymentManagerType, liteEmbeddedDeploymentManagerType)

  private val vowelsRegex = "[aeiouyAEIOUY-]"

  private val componentStatisticPrefix = "c_"

  private val nameForCustom = "Custom"

  private val nameForFragment = "Fragment"

  private val sameNameForComponentInDifferentGroups = List("kafka", "table")

  private def fromNussknackerPackage(component: ComponentDefinitionWithImplementation): Boolean =
    component.component.getClass.getPackageName.startsWith("pl.touk.nussknacker")

  private[statistics] val emptyScenarioStatistics: Map[String, String] = Map(
    ScenarioCount        -> 0,
    FragmentCount        -> 0,
    UnboundedStreamCount -> 0,
    BoundedStreamCount   -> 0,
    RequestResponseCount -> 0,
    FlinkDMCount         -> 0,
    LiteK8sDMCount       -> 0,
    LiteEmbeddedDMCount  -> 0,
    UnknownDMCount       -> 0,
    ActiveScenarioCount  -> 0
  ).map { case (k, v) => (k.toString, v.toString) }

  private[statistics] val emptyActivityStatistics: Map[String, String] = Map(
    AttachmentsAverage -> 0,
    AttachmentsTotal   -> 0,
    CommentsTotal      -> 0,
    CommentsAverage    -> 0
  ).map { case (k, v) => (k.toString, v.toString) }

  private[statistics] val emptyComponentStatistics: Map[String, String] =
    Map(ComponentsCount.toString -> "0")

  private[statistics] val emptyUptimeStats: Map[String, String] = Map(
    UptimeInSecondsAverage -> 0,
    UptimeInSecondsMax     -> 0,
    UptimeInSecondsMin     -> 0,
  ).map { case (k, v) => (k.toString, v.toString) }

  private[statistics] val emptyGeneralStatistics: Map[String, String] = Map(
    NodesMedian            -> 0,
    NodesAverage           -> 0,
    NodesMax               -> 0,
    NodesMin               -> 0,
    CategoriesCount        -> 0,
    VersionsMedian         -> 0,
    VersionsAverage        -> 0,
    VersionsMax            -> 0,
    VersionsMin            -> 0,
    AuthorsCount           -> 0,
    FragmentsUsedMedian    -> 0,
    FragmentsUsedAverage   -> 0,
    UptimeInSecondsAverage -> 0,
    UptimeInSecondsMax     -> 0,
    UptimeInSecondsMin     -> 0,
  ).map { case (k, v) => (k.toString, v.toString) }

  def getScenarioStatistics(scenariosInputData: List[ScenarioStatisticsInputData]): Map[String, String] = {
    emptyScenarioStatistics ++
      scenariosInputData
        .map(ScenarioStatistics.determineStatisticsForScenario)
        .combineAll
        .mapValuesNow(_.toString)
  }

  def getGeneralStatistics(scenariosInputData: List[ScenarioStatisticsInputData]): Map[String, String] = {
    if (scenariosInputData.isEmpty) {
      emptyGeneralStatistics
    } else {
      //        Nodes stats
      val sortedNodes  = scenariosInputData.map(_.nodesCount).sorted
      val nodesMedian  = calculateMedian(sortedNodes)
      val nodesAverage = calculateAverage(sortedNodes)
      val nodesMax     = getMax(sortedNodes)
      val nodesMin     = getMin(sortedNodes)
      //        Category stats
      val categoriesCount = scenariosInputData.map(_.scenarioCategory).toSet.size
      //        Version stats
      val sortedVersions  = scenariosInputData.map(_.scenarioVersion.value).sorted
      val versionsMedian  = calculateMedian(sortedVersions)
      val versionsAverage = calculateAverage(sortedVersions)
      val versionsMax     = getMax(sortedVersions)
      val versionsMin     = getMin(sortedVersions)
      //        Author stats
      val authorsCount = scenariosInputData.map(_.createdBy).toSet.size
      //        Fragment stats
      val fragmentsUsedCount   = scenariosInputData.filterNot(_.isFragment).map(_.fragmentsUsedCount).sorted
      val fragmentsUsedMedian  = calculateMedian(fragmentsUsedCount)
      val fragmentsUsedAverage = calculateAverage(fragmentsUsedCount)
      //          Uptime stats
      val lastActions = scenariosInputData.flatMap(_.lastDeployedAction)
      val sortedUptimes = lastActions.map { action =>
        Instant.now.getEpochSecond - action.performedAt.getEpochSecond
      }.sorted
      val uptimeStatsMap = {
        if (sortedUptimes.isEmpty) {
          emptyUptimeStats
        } else {
          Map(
            UptimeInSecondsAverage -> calculateAverage(sortedUptimes),
            UptimeInSecondsMax     -> getMax(sortedUptimes),
            UptimeInSecondsMin     -> getMin(sortedUptimes)
          ).map { case (k, v) => (k.toString, v.toString) }
        }
      }

      Map(
        NodesMedian          -> nodesMedian,
        NodesAverage         -> nodesAverage,
        NodesMax             -> nodesMax,
        NodesMin             -> nodesMin,
        CategoriesCount      -> categoriesCount,
        VersionsMedian       -> versionsMedian,
        VersionsAverage      -> versionsAverage,
        VersionsMax          -> versionsMax,
        VersionsMin          -> versionsMin,
        AuthorsCount         -> authorsCount,
        FragmentsUsedMedian  -> fragmentsUsedMedian,
        FragmentsUsedAverage -> fragmentsUsedAverage,
      )
        .map { case (k, v) => (k.toString, v.toString) } ++
        uptimeStatsMap
    }
  }

  def getComponentStatistics(
      designerWideUsage: Map[DesignerWideComponentId, Long],
      components: List[ComponentDefinitionWithImplementation]
  ): Map[String, String] = {
    val componentsCount =
      components
        .groupBy(_.id)
        .map { case (componentId, list) =>
          if (list.forall(fromNussknackerPackage)) {
            componentId.toString
          } else nameForCustom
        }
        .size

    val componentUsages = {
      designerWideUsage.toList
        .map { case (designerWideId, usages) =>
          val componentIdOrCustom = components.find(_.designerWideId == designerWideId) match {
            case Some(componentDefinition) =>
              if (fromNussknackerPackage(componentDefinition)) {
                if (sameNameForComponentInDifferentGroups.contains(componentDefinition.name))
                  componentDefinition.id.toString
                else componentDefinition.name
              } else nameForCustom
            case None => nameForFragment
          }
          (componentIdOrCustom, usages)
        }
        .groupBy(_._1)
        .-(nameForFragment)
        .mapValuesNow(list => list.map(_._2).sum)
        .map { case (k, v) => (mapNameToStat(k), v.toString) }
    }

    Map(
      ComponentsCount -> componentsCount
    ).map { case (k, v) => (k.toString, v.toString) } ++
      componentUsages
  }

  def getActivityStatistics(attachmentsAndCommentsTotal: Map[String, Int], scenarioCount: Int): Map[String, String] = {
    if (attachmentsAndCommentsTotal.isEmpty) {
      emptyActivityStatistics
    } else {
      val attachmentsTotal   = attachmentsAndCommentsTotal.getOrElse(AttachmentsTotal.toString, 0)
      val attachmentsAverage = averageOrZero(attachmentsTotal, scenarioCount)
      val commentsTotal      = attachmentsAndCommentsTotal.getOrElse(CommentsTotal.toString, 0)
      val commentsAverage    = averageOrZero(commentsTotal, scenarioCount)
      Map(
        AttachmentsTotal   -> attachmentsTotal,
        AttachmentsAverage -> attachmentsAverage,
        CommentsTotal      -> commentsTotal,
        CommentsAverage    -> commentsAverage
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
      ActiveScenarioCount  -> inputData.status.contains(SimpleStateStatus.Running),
    ).map { case (k, v) => (k.toString, if (v) 1 else 0) }
  }

  private def calculateMedian[T: Numeric](orderedList: List[T]): T = {
    orderedList.get(orderedList.size / 2).getOrElse(implicitly[Numeric[T]].zero)
  }

  private def calculateAverage[T: Numeric](list: List[T]): T = {
    if (list.isEmpty) implicitly[Numeric[T]].zero
    else {
      val result = implicitly[Numeric[T]].toInt(list.sum) / list.length
      implicitly[Numeric[T]].fromInt(result)
    }
  }

  private def averageOrZero(dividend: Int, divisor: Int): Int = {
    if (divisor == 0) {
      0
    } else {
      dividend / divisor
    }
  }

  private def getMax[T: Numeric](orderedList: List[T]): T = {
    if (orderedList.isEmpty) implicitly[Numeric[T]].zero
    else orderedList.last
  }

  private def getMin[T: Numeric](orderedList: List[T]): T = {
    if (orderedList.isEmpty) implicitly[Numeric[T]].zero
    else orderedList.head
  }

  private def mapNameToStat(componentId: String): String = {
    val shortenedName = componentId.replaceAll(vowelsRegex, "").toLowerCase

    componentStatisticPrefix + shortenedName
  }

}

sealed abstract class StatisticKey(val name: String) {
  override def toString: String = name
}

case object AuthorsCount           extends StatisticKey("a_n")
case object CategoriesCount        extends StatisticKey("ca")
case object ComponentsCount        extends StatisticKey("c_n")
case object VersionsMedian         extends StatisticKey("v_m")
case object AttachmentsTotal       extends StatisticKey("a_t")
case object AttachmentsAverage     extends StatisticKey("a_v")
case object VersionsMax            extends StatisticKey("v_ma")
case object VersionsMin            extends StatisticKey("v_mi")
case object VersionsAverage        extends StatisticKey("v_v")
case object UptimeInSecondsAverage extends StatisticKey("u_v")
case object UptimeInSecondsMax     extends StatisticKey("u_ma")
case object UptimeInSecondsMin     extends StatisticKey("u_mi")
case object CommentsAverage        extends StatisticKey("co_v")
case object CommentsTotal          extends StatisticKey("co_t")
case object FragmentsUsedMedian    extends StatisticKey("fr_m")
case object FragmentsUsedAverage   extends StatisticKey("fr_v")
case object NodesMedian            extends StatisticKey("n_m")
case object NodesAverage           extends StatisticKey("n_v")
case object NodesMax               extends StatisticKey("n_ma")
case object NodesMin               extends StatisticKey("n_mi")
case object ScenarioCount          extends StatisticKey("s_s")
case object FragmentCount          extends StatisticKey("s_f")
case object UnboundedStreamCount   extends StatisticKey("s_pm_s")
case object BoundedStreamCount     extends StatisticKey("s_pm_b")
case object RequestResponseCount   extends StatisticKey("s_pm_rr")
case object FlinkDMCount           extends StatisticKey("s_dm_f")
case object LiteK8sDMCount         extends StatisticKey("s_dm_l")
case object LiteEmbeddedDMCount    extends StatisticKey("s_dm_e")
case object UnknownDMCount         extends StatisticKey("s_dm_c")
case object ActiveScenarioCount    extends StatisticKey("s_a")
// Not scenario related statistics
case object NuSource                extends StatisticKey("source") // f.e docker, helmchart, docker-quickstart, binaries
case object NuFingerprint           extends StatisticKey("fingerprint")
case object NuVersion               extends StatisticKey("version")
case object RequestIdStat           extends StatisticKey("req_id")
case object DesignerUptimeInSeconds extends StatisticKey("d_u")
