package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.VersionId
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{SubprocessInputDefinition, SubprocessOutputDefinition, Filter => FilterNodeData}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.component.ComponentModelData._

import java.time.Instant

object ComponentTestProcessData {

  import VersionId._
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

  private val deployedAction = ProcessAction(initialVersionId, Instant.now(), "user", ProcessActionType.Deploy, Option.empty, Option.empty, Map.empty)
  private val canceledAction = ProcessAction(initialVersionId, Instant.now(), "user", ProcessActionType.Cancel, Option.empty, Option.empty, Map.empty)
  private val archivedAction = ProcessAction(initialVersionId, Instant.now(), "user", ProcessActionType.Archive, Option.empty, Option.empty, Map.empty)

  val MarketingProcess: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("marketingProcess", Streaming, SharedSourceConf, SharedSinkConf),
    category = CategoryMarketing
  )

  val FraudProcess: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("fraudProcess", Fraud, SharedSourceConf, SharedSinkConf),
    category = CategoryFraud
  )

  val FraudProcessWithNotSharedSource: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("fraudProcessWithNotSharedSource", Fraud, NotSharedSourceConf, SharedSinkConf),
    category = CategoryFraud
  )

  val FraudTestProcess: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("fraudTestProcess", Fraud, SecondSharedSourceConf, SharedSinkConf),
    category = CategoryFraudTests
  )

  val DeployedFraudProcessWith2Filters: ProcessDetails = displayableToProcess(
    displayable = {
      val process = ScenarioBuilder
        .streaming(DeployedFraudProcessName)
        .source(DefaultSourceName, SharedSourceName)
        .filter(DefaultFilterName, "#input.id != null")
        .filter(SecondFilterName, "#input.id != null")
        .emptySink(DefaultSinkName, DefaultSinkName)

      toDisplayable(process, processingType = Fraud)
    },
    category = CategoryFraud
  ).copy(lastAction = Some(deployedAction))


  val CanceledFraudProcessWith2Enrichers: ProcessDetails = displayableToProcess(
    displayable = {
      val process = ScenarioBuilder
        .streaming(CanceledFraudProcessName)
        .source(DefaultSourceName, SharedSourceName)
        .enricher(DefaultCustomName, "customOut", CustomerDataEnricherName)
        .enricher(SecondCustomName, "secondCustomOut", CustomerDataEnricherName)
        .emptySink(DefaultSinkName, DefaultSinkName)

      toDisplayable(process, processingType = Fraud)
    },
    category = CategoryFraud
  ).copy(lastAction = Some(canceledAction))

  val ArchivedFraudProcess: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("archivedFraudProcess", Fraud, SecondSharedSourceConf, SharedSinkConf),
    isArchived = true,
    category = CategoryFraud
  ).copy(lastAction = Some(archivedAction))

  private val fraudDisplayableSubprocess: DisplayableProcess = createDisplayableSubprocess(
    name = FraudSubprocessName,
    nodes = List(
      SubprocessInputDefinition("fraudStart", List(SubprocessParameter("in", SubprocessClazzRef[String]))),
      FilterNodeData(SubprocessFilterName, "#input.id != null"),
      SubprocessOutputDefinition("fraudEnd", "output", List.empty)
    ),
    processingType = Fraud,
    category = CategoryFraud
  )

  val FraudSubprocess: ProcessDetails = createSubProcess(
    FraudSubprocessName, CategoryFraud, processingType = Fraud, json = Some(fraudDisplayableSubprocess)
  )

  val FraudProcessWithSubprocess: ProcessDetails = displayableToProcess(
    toDisplayable(
      ScenarioBuilder
        .streaming(FraudProcessWithSubprocessName)
        .source(SecondSourceName, SharedSourceName)
        .filter(SecondFilterName, "#input.id != null")
        .subprocess(FraudSubprocess.id, FraudSubprocess.id, Nil, Map.empty, Map(
          "sink" -> GraphBuilder.emptySink(DefaultSinkName, FraudSinkName)
        ))
      , Fraud), category = CategoryFraud
  )

  val WrongCategoryProcess: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("wrongCategory", Fraud, SharedSourceConf, SharedSinkConf),
    category = "wrongCategory"
  )

  private def createSimpleDisplayableProcess(id: String, processingType: String, source: NodeConf, sink: NodeConf): DisplayableProcess = toDisplayable(
    espProcess = {
      ScenarioBuilder
        .streaming(id)
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
