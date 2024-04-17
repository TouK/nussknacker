package pl.touk.nussknacker.ui.statistics

import cats.implicits.{toFoldableOps, toTraverseOps}
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.restmodel.component
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository

import java.time.Instant

object ScenarioStatistics {

  def getGeneralStatistics(scenariosInputData: List[ScenarioStatisticsInputData]): Map[String, String] = {
    if (scenariosInputData.isEmpty) {
      Map.empty
    } else {
      val scenariosCount = scenariosInputData.count(!_.isFragment)
      //        Nodes stats
      val sortedNodes  = scenariosInputData.map(_.nodesCount).sorted
      val nodesMedian  = sortedNodes.get(sortedNodes.length / 2).getOrElse(0)
      val nodesAverage = sortedNodes.sum / scenariosInputData.length
      val nodesMax     = sortedNodes.head
      val nodesMin     = sortedNodes.last
      //        Category stats
      val categoriesCount = scenariosInputData.map(_.scenarioCategory).toSet.size
      //        Version stats
      val sortedVersions  = scenariosInputData.map(_.scenarioVersion.value).sorted
      val versionsMedian  = sortedVersions.get(sortedVersions.length / 2).getOrElse(0)
      val versionsAverage = sortedVersions.sum / scenariosInputData.length
      val versionsMax     = sortedVersions.head
      val versionsMin     = sortedVersions.last
      //        Author stats
      val authorsCount = scenariosInputData.map(_.createdBy).toSet.size
      //        Fragment stats
      val fragments        = scenariosInputData.map(_.fragmentsUsedCount).sorted
      val fragmentsMedian  = fragments.get(fragments.length / 2).getOrElse(0)
      val fragmentsAverage = fragments.sum / scenariosCount
      //          Uptime stats
      val lastActions = scenariosInputData.map(_.lastDeployedAction).filter(_.isDefined).sequence.get
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
        FragmentsAverage -> fragmentsAverage,
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
      val sortedAttachmentCountList = listOfActivities
        .map { activity =>
          activity.attachments
        }
        .map(_.length)
        .sorted
      val attachmentAverage = sortedAttachmentCountList.sum / sortedAttachmentCountList.length
      val attachmentsTotal  = sortedAttachmentCountList.sum
      //        Comment stats
      val comments = listOfActivities
        .map { activity =>
          activity.comments
        }
        .map(_.length)
        .sorted
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
      val componentsByNameCount = withoutFragments.groupBy(_.name)

      Map(
        ComponentsCount -> componentsByNameCount.size
      ).map { case (k, v) => (k.toString, v.toString) }
    }
  }

}

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
