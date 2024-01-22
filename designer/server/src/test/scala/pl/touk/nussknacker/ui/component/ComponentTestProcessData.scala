package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.component.ComponentModelData._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

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

  val FraudFragmentName: ProcessName            = ProcessName("fraudFragmentName")
  val DeployedFraudProcessName: ProcessName     = ProcessName("deployedFraudProcess")
  val CanceledFraudProcessName: ProcessName     = ProcessName("canceledFraudProcessName")
  val FraudProcessWithFragmentName: ProcessName = ProcessName("fraudProcessWithFragment")

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

  val MarketingProcess: ScenarioWithDetailsEntity[DisplayableProcess] = displayableToScenarioWithDetailsEntity(
    displayable = createSimpleDisplayableProcess(SharedSourceConf, SharedSinkConf),
    name = ProcessName("marketingProcess"),
    processingType = Streaming,
    category = CategoryMarketing
  )

  val FraudProcess: ScenarioWithDetailsEntity[DisplayableProcess] = displayableToScenarioWithDetailsEntity(
    displayable = createSimpleDisplayableProcess(SharedSourceConf, SharedSinkConf),
    name = ProcessName("fraudProcess"),
    processingType = Fraud,
    category = CategoryFraud
  )

  val FraudProcessWithNotSharedSource: ScenarioWithDetailsEntity[DisplayableProcess] =
    displayableToScenarioWithDetailsEntity(
      displayable = createSimpleDisplayableProcess(NotSharedSourceConf, SharedSinkConf),
      name = ProcessName("fraudProcessWithNotSharedSource"),
      processingType = Fraud,
      category = CategoryFraud
    )

  val DeployedFraudProcessWith2Filters: ScenarioWithDetailsEntity[DisplayableProcess] =
    displayableToScenarioWithDetailsEntity(
      displayable = {
        val process = ScenarioBuilder
          .streaming("not-used-name")
          .source(DefaultSourceName, SharedSourceName)
          .filter(DefaultFilterName, "#input.id != null")
          .filter(SecondFilterName, "#input.id != null")
          .emptySink(DefaultSinkName, DefaultSinkName)
        ProcessConverter.toDisplayable(process)
      },
      name = DeployedFraudProcessName,
      processingType = Fraud,
      category = CategoryFraud
    ).copy(lastAction = Some(deployedAction))

  val CanceledFraudProcessWith2Enrichers: ScenarioWithDetailsEntity[DisplayableProcess] =
    displayableToScenarioWithDetailsEntity(
      displayable = {
        val process = ScenarioBuilder
          .streaming("not-used-name")
          .source(DefaultSourceName, SharedSourceName)
          .enricher(DefaultCustomName, "customOut", CustomerDataEnricherName)
          .enricher(SecondCustomName, "secondCustomOut", CustomerDataEnricherName)
          .emptySink(DefaultSinkName, DefaultSinkName)
        ProcessConverter.toDisplayable(process)
      },
      name = CanceledFraudProcessName,
      processingType = Fraud,
      category = CategoryFraud
    ).copy(lastAction = Some(canceledAction))

  val ArchivedFraudProcess: ScenarioWithDetailsEntity[DisplayableProcess] = displayableToScenarioWithDetailsEntity(
    displayable = createSimpleDisplayableProcess(SecondSharedSourceConf, SharedSinkConf),
    name = ProcessName("archivedFraudProcess"),
    isArchived = true,
    processingType = Fraud,
    category = CategoryFraud
  ).copy(lastAction = Some(archivedAction))

  val FraudFragment: ScenarioWithDetailsEntity[DisplayableProcess] = displayableToScenarioWithDetailsEntity(
    displayable = {
      val scenario = ScenarioBuilder
        .fragment("not-used-name", "in" -> classOf[String])
        .filter(FragmentFilterName, "#input.id != null")
        .fragmentOutput("fraudEnd", "output")
      ProcessConverter.toDisplayable(scenario)
    },
    name = FraudFragmentName,
    processingType = Fraud,
    category = CategoryFraud,
    isFragment = true,
  )

  val FraudProcessWithFragment: ScenarioWithDetailsEntity[DisplayableProcess] = displayableToScenarioWithDetailsEntity(
    ProcessConverter.toDisplayable(
      ScenarioBuilder
        .streaming("not-used-name")
        .source(SecondSourceName, SharedSourceName)
        .filter(SecondFilterName, "#input.id != null")
        .fragment(
          FraudFragment.name.value,
          FraudFragment.name.value,
          Nil,
          Map.empty,
          Map(
            "sink" -> GraphBuilder.emptySink(DefaultSinkName, FraudSinkName)
          )
        )
    ),
    name = FraudProcessWithFragmentName,
    processingType = Fraud,
    category = CategoryFraud
  )

  private def createSimpleDisplayableProcess(
      source: NodeConf,
      sink: NodeConf
  ): DisplayableProcess = ProcessConverter.toDisplayable(
    ScenarioBuilder
      .streaming("not-used-name")
      .source(source.name, source.id)
      .emptySink(sink.name, sink.id)
  )

  /**
   * @param name - created by user on GUI
   * @param id   - id placed in ProcessConfigCreator
   */
  final case class NodeConf(name: String, id: String)
}
