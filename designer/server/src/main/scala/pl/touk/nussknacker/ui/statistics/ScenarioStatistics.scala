package pl.touk.nussknacker.ui.statistics

import cats.implicits.toFoldableOps
import pl.touk.nussknacker.engine.api.component.{ComponentType, ProcessingMode}
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
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

  def getComponentStatistic(
      componentList: List[component.ComponentListElement],
      components: List[ComponentDefinitionWithImplementation]
  ): Map[String, String] = {
    if (componentList.isEmpty) {
      Map.empty
    } else {
      val builtinNames = List("choice", "filter", "record-variable", "split", "variable")
      // Get number of available components to check how many custom components created
      val withoutFragments = componentList.filterNot(comp => comp.componentType == ComponentType.Fragment)
      val componentsWithUsageByName: Map[String, Long] =
        withoutFragments
          .map { comp =>
            components.find(compo => compo.name.equals(comp.name)) match {
              case Some(comps) =>
                if (comps.component.getClass.toString.contains("pl.touk.nussknacker")) {
                  (comp.name, comp.usageCount)
                } else {
                  ("Custom", comp.usageCount)
                }
              case None =>
                builtinNames.find(builtin => builtin == comp.name) match {
                  case Some(_) => (comp.name.capitalize, comp.usageCount)
                  case None    => ("Custom", comp.usageCount)
                }
            }
          }
          .groupBy(_._1)
          .mapValuesNow(_.map(_._2).sum)

      val componentsWithUsageByNameCount = componentsWithUsageByName.size

//       Get usage statistics for each component
      val componentUsed = componentsWithUsageByName.filter(_._2 > 0)
      val componentUsedMap: Map[String, Long] = componentUsed
        .map { case (name, usages) =>
          (mapNameToStat(name), usages)
        }

      (
        componentUsedMap ++
          Map(
            ComponentsCount.toString -> componentsWithUsageByNameCount
          )
      ).mapValuesNow(_.toString)
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

  def mapNameToStat(name: String): String = {
    val name1 = name.hashCode.abs.toString
    val response = if (name1.length > 4) {
      name1.take(3) + name1.takeRight(2)
    } else {
      name1
    }

    "c_" + response
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
case object NuSource               extends StatisticKey("source") // f.e docker, helmchart, docker-quickstart, binaries
case object NuFingerprint          extends StatisticKey("fingerprint")
case object NuVersion              extends StatisticKey("version")

object ComponentKeys {
  case object Aggregate                         extends StatisticKey("c_17551")
  case object AggregateTumbling                 extends StatisticKey("c_17154")
  case object ListReturnObjectService           extends StatisticKey("c_29628")
  case object ComponentService                  extends StatisticKey("c_14204")
  case object ClassInstanceSource               extends StatisticKey("c_18496")
  case object DatesTypesService                 extends StatisticKey("c_11375")
  case object EchoEnumService                   extends StatisticKey("c_12785")
  case object AccountService                    extends StatisticKey("c_15952")
  case object DynamicService                    extends StatisticKey("c_20086")
  case object OptionalTypesService              extends StatisticKey("c_10992")
  case object Monitor                           extends StatisticKey("c_12378")
  case object AggregateSliding                  extends StatisticKey("c_16172")
  case object HideVariables                     extends StatisticKey("c_16625")
  case object SingleSideJoin                    extends StatisticKey("c_13393")
  case object Delay                             extends StatisticKey("c_95407")
  case object Choice                            extends StatisticKey("c_20177")
  case object Request                           extends StatisticKey("c_10943")
  case object DeadEnd                           extends StatisticKey("c_50142")
  case object EnricherNullResult                extends StatisticKey("c_15050")
  case object MultipleParamsService             extends StatisticKey("c_79981")
  case object UnionWithEditors                  extends StatisticKey("c_84013")
  case object DecisionTable                     extends StatisticKey("c_97305")
  case object RealKafkaJsonSampleProduct        extends StatisticKey("c_20642")
  case object Union                             extends StatisticKey("c_11123")
  case object DbQuery                           extends StatisticKey("c_65913")
  case object KafkaTransaction                  extends StatisticKey("c_16433")
  case object ParamService                      extends StatisticKey("c_74864")
  case object RealKafka                         extends StatisticKey("c_12071")
  case object ModelConfigReader                 extends StatisticKey("c_25422")
  case object OneSource                         extends StatisticKey("c_15949")
  case object Variable                          extends StatisticKey("c_11836")
  case object ConstantStateTransformer          extends StatisticKey("c_19516")
  case object LastVariableWithFilter            extends StatisticKey("c_18664")
  case object Stateful                          extends StatisticKey("c_13136")
  case object EnrichWithAdditionalData          extends StatisticKey("c_15108")
  case object SendCommunication                 extends StatisticKey("c_61882")
  case object ClientHttpService                 extends StatisticKey("c_14778")
  case object FullOuterJoin                     extends StatisticKey("c_20042")
  case object PreviousValue                     extends StatisticKey("c_27510")
  case object DbLookup                          extends StatisticKey("c_11787")
  case object ConfiguratorService               extends StatisticKey("c_12028")
  case object CommunicationSource               extends StatisticKey("c_14941")
  case object Kafka                             extends StatisticKey("c_10110")
  case object Response                          extends StatisticKey("c_34063")
  case object ServiceModelService               extends StatisticKey("c_16911")
  case object ProvidedComponentComponentV1      extends StatisticKey("c_10862")
  case object SimpleTypesCustomNode             extends StatisticKey("c_58482")
  case object DeadEndLite                       extends StatisticKey("c_21465")
  case object CollectionTypesService            extends StatisticKey("c_47854")
  case object UnionReturnObjectService          extends StatisticKey("c_84883")
  case object CampaignService                   extends StatisticKey("c_15997")
  case object ProvidedComponentComponentV2      extends StatisticKey("c_10863")
  case object BoundedSource                     extends StatisticKey("c_86008")
  case object Periodic                          extends StatisticKey("c_43343")
  case object AdditionalVariable                extends StatisticKey("c_12143")
  case object CommunicationSink                 extends StatisticKey("c_29731")
  case object CsvSourceLite                     extends StatisticKey("c_20385")
  case object AggregateSession                  extends StatisticKey("c_18116")
  case object CsvSource                         extends StatisticKey("c_20022")
  case object ServiceWithDictParameterEditor    extends StatisticKey("c_82225")
  case object Collect                           extends StatisticKey("c_94906")
  case object CustomValidatedService            extends StatisticKey("c_18036")
  case object ComplexReturnObjectService        extends StatisticKey("c_28222")
  case object ProvidedComponentComponentV3      extends StatisticKey("c_10864")
  case object KafkaString                       extends StatisticKey("c_16640")
  case object ForEach                           extends StatisticKey("c_41561")
  case object Split                             extends StatisticKey("c_80094")
  case object Enricher                          extends StatisticKey("c_21318")
  case object ConstantStateTransformerLongValue extends StatisticKey("c_72847")
  case object Filter                            extends StatisticKey("c_21024")
  case object SimpleTypesService                extends StatisticKey("c_35634")
  case object GenericSourceWithCustomVariables  extends StatisticKey("c_16974")
  case object SendSms                           extends StatisticKey("c_19729")
  case object UnionMemo                         extends StatisticKey("c_15364")
  case object CustomFilter                      extends StatisticKey("c_52505")
  case object SqlSource                         extends StatisticKey("c_42302")
  case object Log                               extends StatisticKey("c_10732")
  case object DynamicMultipleParamsService      extends StatisticKey("c_14496")
  case object NoneReturnTypeTransformer         extends StatisticKey("c_57025")
  case object Table                             extends StatisticKey("c_11090")
  case object RecordVariable                    extends StatisticKey("c_14128")
  case object MeetingService                    extends StatisticKey("c_13846")
  case object TransactionService                extends StatisticKey("c_13751")
  case object Custom                            extends StatisticKey("c_20265")
}
