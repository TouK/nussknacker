package pl.touk.nussknacker.ui.statistics

import cats.implicits.toFoldableOps
import enumeratum.values.{StringEnum, StringEnumEntry}
import pl.touk.nussknacker.engine.api.component.{BuiltInComponentId, ComponentType, ProcessingMode}
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

  private val builtinNames = BuiltInComponentId.All.map(_.name)

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

      // Get number of available components to check how many custom components created
      val withoutFragments = componentList.filterNot(comp => comp.componentType == ComponentType.Fragment)
      val componentsWithUsageByName: Map[String, Long] =
        withoutFragments
          .map { comp =>
            components.find(compo => compo.name.equals(comp.name)) match {
              case Some(comps) =>
                if (comps.component.getClass.getPackageName.startsWith("pl.touk.nussknacker")) {
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
    val response = name.replaceAll("[aeiouAEIOU-]", "").toLowerCase
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

sealed abstract class ComponentKeys(val value: String) extends StringEnumEntry

object ComponentKeys extends StringEnum[ComponentKeys] {
  case object Aggregate                         extends ComponentKeys("c_ggrgt")
  case object AggregateTumbling                 extends ComponentKeys("c_ggrgttmblng")
  case object ListReturnObjectService           extends ComponentKeys("c_lstrtrnbjctsrvc")
  case object ComponentService                  extends ComponentKeys("c_cmpnntsrvc")
  case object ClassInstanceSource               extends ComponentKeys("c_clssnstncsrc")
  case object DatesTypesService                 extends ComponentKeys("c_dtstypssrvc")
  case object EchoEnumService                   extends ComponentKeys("c_chnmsrvc")
  case object AccountService                    extends ComponentKeys("c_ccntsrvc")
  case object DynamicService                    extends ComponentKeys("c_dynmcsrvc")
  case object OptionalTypesService              extends ComponentKeys("c_ptnltypssrvc")
  case object Monitor                           extends ComponentKeys("c_mntr")
  case object AggregateSliding                  extends ComponentKeys("c_ggrgtsldng")
  case object HideVariables                     extends ComponentKeys("c_hdvrbls")
  case object SingleSideJoin                    extends ComponentKeys("c_snglsdjn")
  case object Delay                             extends ComponentKeys("c_dly")
  case object Choice                            extends ComponentKeys("c_chc")
  case object Request                           extends ComponentKeys("c_rqst")
  case object DeadEnd                           extends ComponentKeys("c_ddnd")
  case object EnricherNullResult                extends ComponentKeys("c_nrchrnllrslt")
  case object MultipleParamsService             extends ComponentKeys("c_mltplprmssrvc")
  case object UnionWithEditors                  extends ComponentKeys("c_nnwthdtrs")
  case object DecisionTable                     extends ComponentKeys("c_dcsntbl")
  case object RealKafkaJsonSampleProduct        extends ComponentKeys("c_rlkfkjsnsmplprdct")
  case object Union                             extends ComponentKeys("c_nn")
  case object DbQuery                           extends ComponentKeys("c_dbqry")
  case object KafkaTransaction                  extends ComponentKeys("c_kfktrnsctn")
  case object ParamService                      extends ComponentKeys("c_prmsrvc")
  case object RealKafka                         extends ComponentKeys("c_rlkfk")
  case object ModelConfigReader                 extends ComponentKeys("c_mdlcnfgrdr")
  case object OneSource                         extends ComponentKeys("c_nsrc")
  case object Variable                          extends ComponentKeys("c_vrbl")
  case object ConstantStateTransformer          extends ComponentKeys("c_cnstntstttrnsfrmr")
  case object LastVariableWithFilter            extends ComponentKeys("c_lstvrblwthfltr")
  case object Stateful                          extends ComponentKeys("c_sttfl")
  case object EnrichWithAdditionalData          extends ComponentKeys("c_nrchwthddtnldt")
  case object SendCommunication                 extends ComponentKeys("c_sndcmmnctn")
  case object ClientHttpService                 extends ComponentKeys("c_clnthttpsrvc")
  case object FullOuterJoin                     extends ComponentKeys("c_flltrjn")
  case object PreviousValue                     extends ComponentKeys("c_prvsvl")
  case object DbLookup                          extends ComponentKeys("c_dblkp")
  case object ConfiguratorService               extends ComponentKeys("c_cnfgrtrsrvc")
  case object CommunicationSource               extends ComponentKeys("c_cmmnctnsrc")
  case object Kafka                             extends ComponentKeys("c_kfk")
  case object Response                          extends ComponentKeys("c_rspns")
  case object ServiceModelService               extends ComponentKeys("c_srvcmdlsrvc")
  case object ProvidedComponentComponentV1      extends ComponentKeys("c_prvddcmpnntcmpnntv1")
  case object SimpleTypesCustomNode             extends ComponentKeys("c_smpltypscstmnd")
  case object DeadEndLite                       extends ComponentKeys("c_ddndlt")
  case object CollectionTypesService            extends ComponentKeys("c_cllctntypssrvc")
  case object UnionReturnObjectService          extends ComponentKeys("c_nnrtrnbjctsrvc")
  case object CampaignService                   extends ComponentKeys("c_cmpgnsrvc")
  case object ProvidedComponentComponentV2      extends ComponentKeys("c_prvddcmpnntcmpnntv2")
  case object BoundedSource                     extends ComponentKeys("c_bnddsrc")
  case object Periodic                          extends ComponentKeys("c_prdc")
  case object AdditionalVariable                extends ComponentKeys("c_ddtnlvrbl")
  case object CommunicationSink                 extends ComponentKeys("c_cmmnctnsnk")
  case object CsvSourceLite                     extends ComponentKeys("c_csvsrclt")
  case object AggregateSession                  extends ComponentKeys("c_ggrgtsssn")
  case object CsvSource                         extends ComponentKeys("c_csvsrc")
  case object ServiceWithDictParameterEditor    extends ComponentKeys("c_srvcwthdctprmtrdtr")
  case object Collect                           extends ComponentKeys("c_cllct")
  case object CustomValidatedService            extends ComponentKeys("c_cstmvldtdsrvc")
  case object ComplexReturnObjectService        extends ComponentKeys("c_cmplxrtrnbjctsrvc")
  case object ProvidedComponentComponentV3      extends ComponentKeys("c_prvddcmpnntcmpnntv3")
  case object KafkaString                       extends ComponentKeys("c_kfkstrng")
  case object ForEach                           extends ComponentKeys("c_frch")
  case object Split                             extends ComponentKeys("c_splt")
  case object Enricher                          extends ComponentKeys("c_nrchr")
  case object ConstantStateTransformerLongValue extends ComponentKeys("c_cnstntstttrnsfrmrlngvl")
  case object Filter                            extends ComponentKeys("c_fltr")
  case object SimpleTypesService                extends ComponentKeys("c_smpltypssrvc")
  case object GenericSourceWithCustomVariables  extends ComponentKeys("c_gnrcsrcwthcstmvrbls")
  case object SendSms                           extends ComponentKeys("c_sndsms")
  case object UnionMemo                         extends ComponentKeys("c_nnmm")
  case object CustomFilter                      extends ComponentKeys("c_cstmfltr")
  case object SqlSource                         extends ComponentKeys("c_sqlsrc")
  case object Log                               extends ComponentKeys("c_lg")
  case object DynamicMultipleParamsService      extends ComponentKeys("c_dynmcmltplprmssrvc")
  case object NoneReturnTypeTransformer         extends ComponentKeys("c_nnrtrntyptrnsfrmr")
  case object Table                             extends ComponentKeys("c_tbl")
  case object RecordVariable                    extends ComponentKeys("c_rcrdvrbl")
  case object MeetingService                    extends ComponentKeys("c_mtngsrvc")
  case object TransactionService                extends ComponentKeys("c_trnsctnsrvc")
  case object Custom                            extends ComponentKeys("c_cstm")

  val values: IndexedSeq[ComponentKeys] = findValues
}
