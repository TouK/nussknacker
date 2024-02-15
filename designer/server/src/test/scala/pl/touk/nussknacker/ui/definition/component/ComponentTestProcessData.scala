package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.test.utils.domain.TestProcessUtil.wrapGraphWithScenarioDetailsEntity
import pl.touk.nussknacker.ui.definition.component.ComponentModelData._
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

import java.time.Instant
import java.util.UUID

private[component] object ComponentTestProcessData {

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

  val MarketingProcess: ScenarioWithDetailsEntity[ScenarioGraph] = wrapGraphWithScenarioDetailsEntity(
    scenarioGraph = createSimpleDisplayableProcess(SharedSourceConf, SharedSinkConf),
    name = ProcessName("marketingProcess"),
    processingType = ProcessingTypeStreaming,
    category = CategoryMarketing
  )

  val FraudProcess: ScenarioWithDetailsEntity[ScenarioGraph] = wrapGraphWithScenarioDetailsEntity(
    scenarioGraph = createSimpleDisplayableProcess(SharedSourceConf, SharedSinkConf),
    name = ProcessName("fraudProcess"),
    processingType = ProcessingTypeFraud,
    category = CategoryFraud
  )

  val FraudProcessWithNotSharedSource: ScenarioWithDetailsEntity[ScenarioGraph] =
    wrapGraphWithScenarioDetailsEntity(
      scenarioGraph = createSimpleDisplayableProcess(NotSharedSourceConf, SharedSinkConf),
      name = ProcessName("fraudProcessWithNotSharedSource"),
      processingType = ProcessingTypeFraud,
      category = CategoryFraud
    )

  val DeployedFraudProcessWith2Filters: ScenarioWithDetailsEntity[ScenarioGraph] =
    wrapGraphWithScenarioDetailsEntity(
      scenarioGraph = {
        val process = ScenarioBuilder
          .streaming("not-used-name")
          .source(DefaultSourceName, SharedSourceName)
          .filter(DefaultFilterName, "#input.id != null")
          .filter(SecondFilterName, "#input.id != null")
          .emptySink(DefaultSinkName, DefaultSinkName)
        CanonicalProcessConverter.toScenarioGraph(process)
      },
      name = DeployedFraudProcessName,
      processingType = ProcessingTypeFraud,
      category = CategoryFraud
    ).copy(lastAction = Some(deployedAction))

  val CanceledFraudProcessWith2Enrichers: ScenarioWithDetailsEntity[ScenarioGraph] =
    wrapGraphWithScenarioDetailsEntity(
      scenarioGraph = {
        val process = ScenarioBuilder
          .streaming("not-used-name")
          .source(DefaultSourceName, SharedSourceName)
          .enricher(DefaultCustomName, "customOut", CustomerDataEnricherName)
          .enricher(SecondCustomName, "secondCustomOut", CustomerDataEnricherName)
          .emptySink(DefaultSinkName, DefaultSinkName)
        CanonicalProcessConverter.toScenarioGraph(process)
      },
      name = CanceledFraudProcessName,
      processingType = ProcessingTypeFraud,
      category = CategoryFraud
    ).copy(lastAction = Some(canceledAction))

  val ArchivedFraudProcess: ScenarioWithDetailsEntity[ScenarioGraph] = wrapGraphWithScenarioDetailsEntity(
    scenarioGraph = createSimpleDisplayableProcess(SecondSharedSourceConf, SharedSinkConf),
    name = ProcessName("archivedFraudProcess"),
    isArchived = true,
    processingType = ProcessingTypeFraud,
    category = CategoryFraud
  ).copy(lastAction = Some(archivedAction))

  val FraudFragment: ScenarioWithDetailsEntity[ScenarioGraph] = wrapGraphWithScenarioDetailsEntity(
    scenarioGraph = {
      val scenario = ScenarioBuilder
        .fragment("not-used-name", "in" -> classOf[String])
        .filter(FragmentFilterName, "#input.id != null")
        .fragmentOutput("fraudEnd", "output")
      CanonicalProcessConverter.toScenarioGraph(scenario)
    },
    name = FraudFragmentName,
    processingType = ProcessingTypeFraud,
    category = CategoryFraud,
    isFragment = true,
  )

  val FraudProcessWithFragment: ScenarioWithDetailsEntity[ScenarioGraph] = wrapGraphWithScenarioDetailsEntity(
    name = FraudProcessWithFragmentName,
    scenarioGraph = CanonicalProcessConverter.toScenarioGraph(
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
    processingType = ProcessingTypeFraud,
    category = CategoryFraud
  )

  private def createSimpleDisplayableProcess(
      source: NodeConf,
      sink: NodeConf
  ): ScenarioGraph = CanonicalProcessConverter.toScenarioGraph(
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
