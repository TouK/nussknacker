package pl.touk.nussknacker.ui.statistics

import cats.implicits.toFoldableOps
import pl.touk.nussknacker.engine.api.component.{ComponentType, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.restmodel.component
import pl.touk.nussknacker.ui.process.processingtype.DeploymentManagerType
import pl.touk.nussknacker.ui.process.repository.DbProcessActivityRepository
import pl.touk.nussknacker.ui.statistics.ComponentKeys.Custom

import java.time.Instant

object ScenarioStatistics {

  private val flinkDeploymentManagerType = DeploymentManagerType("flinkStreaming")

  private val liteK8sDeploymentManagerType = DeploymentManagerType("lite-k8s")

  private val liteEmbeddedDeploymentManagerType = DeploymentManagerType("lite-embedded")

  private val knownDeploymentManagerTypes =
    Set(flinkDeploymentManagerType, liteK8sDeploymentManagerType, liteEmbeddedDeploymentManagerType)

  def getScenarioStatistics(scenariosInputData: List[ScenarioStatisticsInputData]): Map[String, String] = {
    scenariosInputData
      .map(ScenarioStatistics.determineStatisticsForScenario)
      .combineAll
      .mapValuesNow(_.toString)
  }

  def getGeneralStatistics(scenariosInputData: List[ScenarioStatisticsInputData]): Map[String, String] = {
    if (scenariosInputData.isEmpty) {
      Map.empty
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
          Map(
            UptimeInSecondsAverage -> 0,
            UptimeInSecondsMax     -> 0,
            UptimeInSecondsMin     -> 0,
          )
        } else {
          Map(
            UptimeInSecondsAverage -> calculateAverage(sortedUptimes),
            UptimeInSecondsMax     -> getMax(sortedUptimes),
            UptimeInSecondsMin     -> getMin(sortedUptimes)
          )
        }
      }

      (Map(
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
        FragmentsUsedAverage -> fragmentsUsedAverage
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
      val sortedAttachmentCountList = listOfActivities.map(_.attachments.length)
      val attachmentAverage         = calculateAverage(sortedAttachmentCountList)
      val attachmentsTotal          = sortedAttachmentCountList.sum
      //        Comment stats
      val comments        = listOfActivities.map(_.comments.length)
      val commentsTotal   = comments.sum
      val commentsAverage = calculateAverage(comments)

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
      // Get number of available components to check how many custom components created
      val withoutFragments = componentList.filterNot(comp => comp.componentType == ComponentType.Fragment)
      val componentsWithUsageByName: Map[String, Long] =
        withoutFragments
          .groupBy(_.name)
          .mapValuesNow(_.map(_.usageCount).sum)
      val componentsWithUsageByNameCount = componentsWithUsageByName.size

      // Get usage statistics for each component
      val componentUsed = componentsWithUsageByName.filter(_._2 > 0)
      val componentUsedMap: Map[StatisticKey, Long] = componentUsed
        .map { case (name, usages) =>
          (mapComponentNameToStatisticKey(name), usages)
        }
        .groupBy(_._1)
        .mapValuesNow(_.values.sum)

      (
        componentUsedMap ++
          Map(
            ComponentsCount -> componentsWithUsageByNameCount
          )
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

  private def getMax[T: Numeric](orderedList: List[T]): T = {
    if (orderedList.isEmpty) implicitly[Numeric[T]].zero
    else orderedList.head
  }

  private def getMin[T: Numeric](orderedList: List[T]): T = {
    if (orderedList.isEmpty) implicitly[Numeric[T]].zero
    else orderedList.last
  }

  private def mapComponentNameToStatisticKey(name: String): StatisticKey =
    ComponentMap.componentMap.getOrElse(name, Custom)
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
case object FragmentsUsedMedian    extends StatisticKey("f_m")
case object FragmentsUsedAverage   extends StatisticKey("f_v")
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
case object NuSource               extends StatisticKey("source") // f.e docker, helmchart, docker-quickstart, binaries
case object NuFingerprint          extends StatisticKey("fingerprint")
case object NuVersion              extends StatisticKey("version")

object ComponentKeys {
  case object Aggregate                         extends StatisticKey("c_ag")
  case object AggregateTumbling                 extends StatisticKey("c_agt")
  case object ComponentService                  extends StatisticKey("c_cos")
  case object DatesTypesService                 extends StatisticKey("c_dts")
  case object AccountService                    extends StatisticKey("c_acs")
  case object DynamicService                    extends StatisticKey("c_dsv")
  case object OptionalTypesService              extends StatisticKey("c_ots")
  case object Monitor                           extends StatisticKey("c_mon")
  case object AggregateSliding                  extends StatisticKey("c_agsl")
  case object HideVariables                     extends StatisticKey("c_hv")
  case object SingleSideJoin                    extends StatisticKey("c_ssj")
  case object Delay                             extends StatisticKey("c_dy")
  case object Request                           extends StatisticKey("c_req")
  case object DeadEnd                           extends StatisticKey("c_dea")
  case object EnricherNullResult                extends StatisticKey("c_enr")
  case object MultipleParamsService             extends StatisticKey("c_mps")
  case object UnionWithEditors                  extends StatisticKey("c_uwe")
  case object DecisionTable                     extends StatisticKey("c_dt")
  case object RealKafkaJsonSampleProduct        extends StatisticKey("c_rkj")
  case object Union                             extends StatisticKey("c_un")
  case object DbQuery                           extends StatisticKey("c_dq")
  case object KafkaTransaction                  extends StatisticKey("c_kt")
  case object ParamService                      extends StatisticKey("c_ps")
  case object RealKafka                         extends StatisticKey("c_rk")
  case object ModelConfigReader                 extends StatisticKey("c_mcr")
  case object OneSource                         extends StatisticKey("c_os")
  case object ConstantStateTransformer          extends StatisticKey("c_cst")
  case object LastVariableWithFilter            extends StatisticKey("c_lvw")
  case object Stateful                          extends StatisticKey("c_st")
  case object EnrichWithAdditionalData          extends StatisticKey("c_ewa")
  case object SendCommunication                 extends StatisticKey("c_sc")
  case object ClientHttpService                 extends StatisticKey("c_chs")
  case object FullOuterJoin                     extends StatisticKey("c_foj")
  case object PreviousValue                     extends StatisticKey("c_pv")
  case object DbLookup                          extends StatisticKey("c_dl")
  case object ConfiguratorService               extends StatisticKey("c_con")
  case object CommunicationSource               extends StatisticKey("c_com")
  case object ListReturnObjectService           extends StatisticKey("c_lro")
  case object Filter                            extends StatisticKey("c_fil")
  case object Kafka                             extends StatisticKey("c_ka")
  case object Response                          extends StatisticKey("c_resp")
  case object ServiceModelService               extends StatisticKey("c_sms")
  case object ProvidedComponentComponentV1      extends StatisticKey("c_pcv1")
  case object SimpleTypesCustomNode             extends StatisticKey("c_stc")
  case object DeadEndLite                       extends StatisticKey("c_del")
  case object CollectionTypesService            extends StatisticKey("c_cts")
  case object UnionReturnObjectService          extends StatisticKey("c_uro")
  case object CampaignService                   extends StatisticKey("c_cas")
  case object ProvidedComponentComponentV2      extends StatisticKey("c_pcv2")
  case object Split                             extends StatisticKey("c_spl")
  case object ClassInstanceSource               extends StatisticKey("c_cis")
  case object Variable                          extends StatisticKey("c_var")
  case object BoundedSource                     extends StatisticKey("c_bs")
  case object Periodic                          extends StatisticKey("c_per")
  case object AdditionalVariable                extends StatisticKey("c_adv")
  case object CommunicationSink                 extends StatisticKey("c_coms")
  case object EchoEnumService                   extends StatisticKey("c_esv")
  case object Choice                            extends StatisticKey("c_cho")
  case object CsvSourceLite                     extends StatisticKey("c_csl")
  case object AggregateSession                  extends StatisticKey("c_agse")
  case object CsvSource                         extends StatisticKey("c_csv")
  case object ServiceWithDictParameterEditor    extends StatisticKey("c_swd")
  case object Collect                           extends StatisticKey("c_col")
  case object CustomValidatedService            extends StatisticKey("c_cuv")
  case object ComplexReturnObjectService        extends StatisticKey("c_cro")
  case object ProvidedComponentComponentV3      extends StatisticKey("c_pcv3")
  case object KafkaString                       extends StatisticKey("c_ks")
  case object ForEach                           extends StatisticKey("c_fe")
  case object Enricher                          extends StatisticKey("c_en")
  case object ConstantStateTransformerLongValue extends StatisticKey("c_cstl")
  case object SimpleTypesService                extends StatisticKey("c_sts")
  case object GenericSourceWithCustomVariables  extends StatisticKey("c_gsw")
  case object SendSms                           extends StatisticKey("c_ss")
  case object UnionMemo                         extends StatisticKey("c_um")
  case object CustomFilter                      extends StatisticKey("c_cf")
  case object SqlSource                         extends StatisticKey("c_sqs")
  case object Log                               extends StatisticKey("c_log")
  case object RecordVariable                    extends StatisticKey("c_rv")
  case object DynamicMultipleParamsService      extends StatisticKey("c_dmp")
  case object NoneReturnTypeTransformer         extends StatisticKey("c_nrt")
  case object Table                             extends StatisticKey("c_tab")
  case object MeetingService                    extends StatisticKey("c_msv")
  case object TransactionService                extends StatisticKey("c_tsv")
  case object Custom                            extends StatisticKey("c_cus")
}
