package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.component.ComponentModelData._

import java.time.Instant
import java.util.UUID

object ComponentTestProcessData {

  import VersionId._
  import pl.touk.nussknacker.engine.spel.Implicits._

  val DefaultSourceName = "source"
  val SecondSourceName  = "secondSource"

  val DefaultSinkName = "sink"

  val DefaultFilterName  = "someFilter"
  val SecondFilterName   = "someFilter2"
  val FragmentFilterName = "fragmentFilter"

  val DefaultCustomName = "customEnricher"
  val SecondCustomName  = "secondCustomEnricher"

  val SecondSharedSourceConf: NodeConf = NodeConf(SecondSourceName, SharedSourceName)
  val SharedSourceConf: NodeConf       = NodeConf(DefaultSourceName, SharedSourceName)
  val NotSharedSourceConf: NodeConf    = NodeConf(DefaultSourceName, NotSharedSourceName)
  val SharedSinkConf: NodeConf         = NodeConf(DefaultSinkName, SharedSinkName)

  val DeployedMarketingProcessName = "deployedMarketingProcess"

  val FraudFragmentName            = "fraudFragmentName"
  val DeployedFraudProcessName     = "deployedFraudProcess"
  val CanceledFraudProcessName     = "canceledFraudProcessName"
  val FraudProcessWithFragmentName = "fraudProcessWithFragment"

  private val deployedAction = prepareTestAction(ProcessActionType.Deploy)
  private val canceledAction = prepareTestAction(ProcessActionType.Cancel)
  private val archivedAction = prepareTestAction(ProcessActionType.Archive)

  private def prepareTestAction(actionType: ProcessActionType) =
    ProcessAction(
      id = ProcessActionId(UUID.randomUUID()),
      processId = ProcessId(123),
      processVersionId = initialVersionId,
      user = "user",
      createdAt = Instant.now(),
      performedAt = Instant.now(),
      actionType = actionType,
      state = ProcessActionState.Finished,
      failureMessage = Option.empty,
      commentId = Option.empty,
      comment = Option.empty,
      buildInfo = Map.empty
    )

  val MarketingProcess: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("marketingProcess", Streaming, SharedSourceConf, SharedSinkConf),
    category = CategoryMarketing
  )

  val FraudProcess: ProcessDetails = displayableToProcess(
    displayable = createSimpleDisplayableProcess("fraudProcess", Fraud, SharedSourceConf, SharedSinkConf),
    category = CategoryFraud
  )

  val FraudProcessWithNotSharedSource: ProcessDetails = displayableToProcess(
    displayable =
      createSimpleDisplayableProcess("fraudProcessWithNotSharedSource", Fraud, NotSharedSourceConf, SharedSinkConf),
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

  val FraudFragment: ProcessDetails = displayableToProcess(
    displayable = {
      val scenario = ScenarioBuilder
        .fragment(FraudFragmentName, "in" -> classOf[String])
        .filter(FragmentFilterName, "#input.id != null")
        .fragmentOutput("fraudEnd", "output")
      toDisplayable(scenario, processingType = Fraud)
    },
    category = CategoryFraud,
    isFragment = true,
  )

  val FraudProcessWithFragment: ProcessDetails = displayableToProcess(
    toDisplayable(
      ScenarioBuilder
        .streaming(FraudProcessWithFragmentName)
        .source(SecondSourceName, SharedSourceName)
        .filter(SecondFilterName, "#input.id != null")
        .fragment(
          FraudFragment.id,
          FraudFragment.id,
          Nil,
          Map.empty,
          Map(
            "sink" -> GraphBuilder.emptySink(DefaultSinkName, FraudSinkName)
          )
        ),
      Fraud
    ),
    category = CategoryFraud
  )

  private def createSimpleDisplayableProcess(
      id: String,
      processingType: String,
      source: NodeConf,
      sink: NodeConf
  ): DisplayableProcess = toDisplayable(
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
   * @param id   - id placed in ProcessConfigCreator
   */
  final case class NodeConf(name: String, id: String)
}
