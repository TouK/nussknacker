package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{SubprocessInputDefinition, SubprocessOutputDefinition, Filter => FilterNodeData}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessAction, ProcessDetails}
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.toDetails
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.component.ComponentModelData._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.subprocess.SubprocessDetails

import java.time.LocalDateTime

object ComponentTestProcessData {

  import pl.touk.nussknacker.engine.spel.Implicits._

  val DefaultSourceName = "source"
  val SecondSourceName = "secondSource"

  val DefaultSinkName = "sink"

  val DefaultFilterName = "someFilter"
  val SecondFilterName = "someFilter2"
  val SubprocessFilterName = "subProcessFilter"

  val DefaultCustomName = "customEnricher"
  val SecondCustomName = "secondCustomEnricher"

  val SecondSharedSourceConf: NodeConf = NodeConf(SecondSourceName, SharedSourceName)
  val SharedSourceConf: NodeConf = NodeConf(DefaultSourceName, SharedSourceName)
  val NotSharedSourceConf: NodeConf = NodeConf(DefaultSourceName, NotSharedSourceName)
  val SharedSinkConf: NodeConf = NodeConf(DefaultSinkName, SharedSinkName)

  val DeployedMarketingProcessName = "deployedMarketingProcess"

  val FraudSubprocessName = "fraudSubprocessName"
  val DeployedFraudProcessName = "deployedFraudProcess"
  val CanceledFraudProcessName = "canceledFraudProcessName"
  val FraudProcessWithSubprocessName = "fraudProcessWithSubprocess"

  private val deployedAction = ProcessAction(VersionId(1), LocalDateTime.now(), "user", ProcessActionType.Deploy, Option.empty, Option.empty, Map.empty)
  private val canceledAction = ProcessAction(VersionId(1), LocalDateTime.now(), "user", ProcessActionType.Cancel, Option.empty, Option.empty, Map.empty)
  private val archivedAction = ProcessAction(VersionId(1), LocalDateTime.now(), "user", ProcessActionType.Archive, Option.empty, Option.empty, Map.empty)

  val MarketingProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("marketingProcess", Streaming, SharedSourceConf, SharedSinkConf),
    category = CategoryMarketing
  )

  val FraudProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("fraudProcess", Fraud, SharedSourceConf, SharedSinkConf),
    category = CategoryFraud
  )

  val FraudProcessWithNotSharedSource: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("fraudProcessWithNotSharedSource", Fraud, NotSharedSourceConf, SharedSinkConf),
    category = CategoryFraud
  )

  val FraudTestProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("fraudTestProcess", Fraud, SecondSharedSourceConf, SharedSinkConf),
    category = CategoryFraudTests
  )

  val DeployedFraudProcessWith2Filters: ProcessDetails = toDetails(
    displayable = {
      val process = EspProcessBuilder
        .id(DeployedFraudProcessName)
        .exceptionHandler()
        .source(NotSharedSourceName, NotSharedSourceName)
        .filter(DefaultFilterName, "#input.id != null")
        .filter(SecondFilterName, "#input.id != null")
        .emptySink(DefaultSinkName, DefaultSinkName)

      TestProcessUtil.toDisplayable(process, processingType = Fraud)
    },
    category = CategoryFraud
  ).copy(lastAction = Some(deployedAction))


  val CanceledFraudProcessWith2Customs: ProcessDetails = toDetails(
    displayable = {
      val process = EspProcessBuilder
        .id(CanceledFraudProcessName)
        .exceptionHandler()
        .source(DefaultSourceName, NotSharedSourceName)
        .customNode(DefaultCustomName, "customOut", CustomerDataEnricherName)
        .customNode(SecondCustomName, "secondCustomOut", CustomerDataEnricherName)
        .emptySink(DefaultSinkName, DefaultSinkName)

      TestProcessUtil.toDisplayable(process, processingType = Fraud)
    },
    category = CategoryFraud
  ).copy(lastAction = Some(canceledAction))

  val ArchivedFraudProcess: BaseProcessDetails[DisplayableProcess] = toDetails(
    displayable = createSimpleDisplayableProcess("archivedFraudProcess", Fraud, SecondSharedSourceConf, SharedSinkConf),
    isArchived = true,
    category = CategoryFraud
  ).copy(lastAction = Some(archivedAction))

  val CanonicalFraudSubprocess: CanonicalProcess = CanonicalProcess(
    MetaData(FraudSubprocessName, FragmentSpecificData()), null,
    List(
      FlatNode(SubprocessInputDefinition("fraudStart", List(SubprocessParameter("in", SubprocessClazzRef[String])))),
      FlatNode(FilterNodeData(SubprocessFilterName, "#input.id != null")),
      FlatNode(SubprocessOutputDefinition("fraudEnd", "output", List.empty))
    ), List.empty
  )

  val FraudSubprocess: ProcessDetails = toDetails(
    ProcessConverter.toDisplayable(CanonicalFraudSubprocess, Fraud),
    category = CategoryFraud
  )

  val FraudSubprocessDetails: SubprocessDetails = SubprocessDetails(CanonicalFraudSubprocess, CategoryFraud)

  val FraudProcessWithSubprocess: ProcessDetails = toDetails(
    TestProcessUtil.toDisplayable(
      EspProcessBuilder
        .id(FraudProcessWithSubprocessName)
        .exceptionHandler()
        .source(SecondSourceName, SharedSourceName)
        .filter(SecondFilterName, "#input.id != null")
        .subprocess(CanonicalFraudSubprocess.metaData.id, CanonicalFraudSubprocess.metaData.id, Nil, Map(
          "sink" -> GraphBuilder.emptySink(DefaultSinkName, FraudSinkName)
        ))
      , Fraud), category = CategoryFraud
  )

  val WrongCategoryProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("wrongCategory", Fraud, SharedSourceConf, SharedSinkConf),
    category = "wrongCategory"
  )

  private def createSimpleDisplayableProcess(id: String, processingType: String, source: NodeConf, sink: NodeConf): DisplayableProcess = TestProcessUtil.toDisplayable(
    espProcess = {
      EspProcessBuilder
        .id(id)
        .exceptionHandler()
        .source(source.name, source.id)
        .emptySink(sink.name, sink.id)
    },
    processingType = processingType
  )

  /**
    * @param name - created by user on GUI
    * @param id - id placed in ProcessConfigCreator
    */
  case class NodeConf(name: String, id: String)
}
