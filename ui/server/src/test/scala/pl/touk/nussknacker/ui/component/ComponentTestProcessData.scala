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

  val SecondSharedSourceConf: NodeConf = NodeConf(SecondSourceName, sharedSourceName)
  val SharedSourceConf: NodeConf = NodeConf(DefaultSourceName, sharedSourceName)
  val SharedSinkConf: NodeConf = NodeConf(DefaultSinkName, sharedSinkName)

  val fraudSubprocessName = "fraudSubprocessName"
  val deployedFraudProcessName = "deployedFraudProcess"
  val canceledFraudProcessName = "canceledFraudProcessName"
  val fraudProcessWithSubprocessName = "fraudProcessWithSubprocess"

  private val deployedAction = ProcessAction(VersionId(1), LocalDateTime.now(), "user", ProcessActionType.Deploy, Option.empty, Option.empty, Map.empty)
  private val canceledAction = ProcessAction(VersionId(1), LocalDateTime.now(), "user", ProcessActionType.Cancel, Option.empty, Option.empty, Map.empty)
  private val archivedAction = ProcessAction(VersionId(1), LocalDateTime.now(), "user", ProcessActionType.Archive, Option.empty, Option.empty, Map.empty)

  val marketingProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("marketingProcess", Streaming, SecondSharedSourceConf, SharedSinkConf),
    category = categoryMarketing
  )

  val fraudProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("fraudProcess", Fraud, SharedSourceConf, SharedSinkConf),
    category = categoryFraud
  )

  val fraudTestProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("fraudTestProcess", Fraud, SharedSourceConf, SharedSinkConf),
    category = categoryFraudTests
  )

  val deployedFraudProcessWith2Filters: ProcessDetails = toDetails(
    displayable = {
      val process = EspProcessBuilder
        .id(deployedFraudProcessName)
        .exceptionHandler()
        .source(fraudSourceName, fraudSourceName)
        .filter(DefaultFilterName, "#input.id != null")
        .filter(SecondFilterName, "#input.id != null")
        .emptySink(DefaultSinkName, DefaultSinkName)

      TestProcessUtil.toDisplayable(process, processingType = Fraud)
    },
    category = categoryFraud
  ).copy(lastAction = Some(deployedAction))

  val canceledFraudProcessWith2Customs: ProcessDetails = toDetails(
    displayable = {
      val process = EspProcessBuilder
        .id(canceledFraudProcessName)
        .exceptionHandler()
        .source(DefaultSourceName, fraudSourceName)
        .customNode(DefaultCustomName, "customOut", customerDataEnricherName)
        .customNode(SecondCustomName, "secondCustomOut", customerDataEnricherName)
        .emptySink(DefaultSinkName, DefaultSinkName)

      TestProcessUtil.toDisplayable(process, processingType = Fraud)
    },
    category = categoryFraud
  ).copy(lastAction = Some(canceledAction))

  val archivedFraudProcess: BaseProcessDetails[DisplayableProcess] = toDetails(
    displayable = createSimpleDisplayableProcess("archivedFraudProcess", Fraud, SecondSharedSourceConf, SharedSinkConf),
    isArchived = true,
    category = categoryFraud
  ).copy(lastAction = Some(archivedAction))

  val canonicalFraudSubprocess: CanonicalProcess = CanonicalProcess(
    MetaData(fraudSubprocessName, FragmentSpecificData()), null,
    List(
      FlatNode(SubprocessInputDefinition("fraudStart", List(SubprocessParameter("in", SubprocessClazzRef[String])))),
      FlatNode(FilterNodeData(SubprocessFilterName, "#input.id != null")),
      FlatNode(SubprocessOutputDefinition("fraudEnd", "output", List.empty))
    ), List.empty
  )

  val fraudSubprocess: ProcessDetails = toDetails(
    ProcessConverter.toDisplayable(canonicalFraudSubprocess, Fraud),
    category = categoryFraud
  )

  val fraudSubprocessDetails: SubprocessDetails = SubprocessDetails(canonicalFraudSubprocess, categoryFraud)

  val fraudProcessWithSubprocess: ProcessDetails = toDetails(
    TestProcessUtil.toDisplayable(
      EspProcessBuilder
        .id(fraudProcessWithSubprocessName)
        .exceptionHandler()
        .source(SecondSourceName, fraudSourceName)
        .filter(SecondFilterName, "#input.id != null")
        .subprocess(canonicalFraudSubprocess.metaData.id, canonicalFraudSubprocess.metaData.id, Nil, Map(
          "sink" -> GraphBuilder.emptySink(DefaultSinkName, fraudSinkName)
        ))
      , Fraud), category = categoryFraud
  )

  val wrongCategoryProcess: ProcessDetails = toDetails(
    displayable = createSimpleDisplayableProcess("wrongCategory", Fraud, SharedSourceConf, SharedSinkConf),
    category = "wrongCategory"
  )

  def createSimpleDisplayableProcess(id: String, processingType: String, source: NodeConf, sink: NodeConf): DisplayableProcess = TestProcessUtil.toDisplayable(
    espProcess = {
      EspProcessBuilder
        .id(id)
        .exceptionHandler()
        .source(source.id, source.`type`)
        .emptySink(sink.id, sink.`type`)
    },
    processingType = processingType
  )

  object NodeConf {
    def apply(id: String): NodeConf = NodeConf(id, id)
  }

  case class NodeConf(id: String, `type`: String)
}
