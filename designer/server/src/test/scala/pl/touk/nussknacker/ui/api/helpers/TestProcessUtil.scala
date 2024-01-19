package pl.touk.nussknacker.ui.api.helpers

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, NodeData}
import pl.touk.nussknacker.restmodel.scenariodetails._
import pl.touk.nussknacker.restmodel.validation.ValidatedDisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, Streaming}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity
import pl.touk.nussknacker.ui.process.{ScenarioWithDetailsConversions, repository}

import java.time.Instant
import java.util.UUID
import scala.util.Random

object TestProcessUtil {

  private val randomGenerator = new Random()

  def toCanonical(displayable: DisplayableProcess, processName: ProcessName): CanonicalProcess =
    ProcessConverter.fromDisplayable(displayable, processName)

  def toJson(espProcess: CanonicalProcess): Json =
    Encoder[DisplayableProcess].apply(ProcessConverter.toDisplayable(espProcess))

  def createScenarioEntity(
      name: String,
      category: Category,
      isArchived: Boolean = false,
      processingType: String = Streaming,
      lastAction: Option[ProcessActionType] = None,
      json: Option[DisplayableProcess] = None
  ): ScenarioWithDetailsEntity[DisplayableProcess] =
    toScenarioWithDetailsEntity(
      ProcessName(name),
      category,
      isFragment = false,
      isArchived,
      processingType,
      json = json,
      lastAction = lastAction
    )

  def createFragmentEntity(
      name: String,
      category: Category,
      isArchived: Boolean = false,
      processingType: String = Streaming,
      json: Option[DisplayableProcess] = None,
      lastAction: Option[ProcessActionType] = None
  ): ScenarioWithDetailsEntity[DisplayableProcess] = {
    val processName = ProcessName(name)
    toScenarioWithDetailsEntity(
      name = processName,
      category = category,
      isFragment = true,
      isArchived = isArchived,
      processingType = processingType,
      lastAction = lastAction,
      json = Some(json.getOrElse(sampleDisplayableFragment))
    )
  }

  def displayableToScenarioWithDetailsEntity(
      displayable: DisplayableProcess,
      name: ProcessName,
      processingType: ProcessingType = TestProcessingTypes.Streaming,
      category: Category = TestCategories.Category1,
      isArchived: Boolean = false,
      isFragment: Boolean = false
  ): ScenarioWithDetailsEntity[DisplayableProcess] =
    toScenarioWithDetailsEntity(
      name,
      processingType = processingType,
      category = category,
      isArchived = isArchived,
      json = Some(displayable),
      isFragment = isFragment
    )

  def wrapWithDetails(
      displayable: DisplayableProcess,
      name: ProcessName = ProcessTestData.sampleProcessName,
      isFragment: Boolean = false,
      validationResult: ValidationResult = ValidationResult.success
  ): ScenarioWithDetails = {
    ScenarioWithDetailsConversions.fromEntity(
      toScenarioWithDetailsEntity(name, isFragment = isFragment)
        .copy(json = ValidatedDisplayableProcess(displayable, validationResult))
    )
  }

  def toScenarioWithDetailsEntity(
      name: ProcessName,
      category: Category = TestCategories.Category1,
      isFragment: Boolean = false,
      isArchived: Boolean = false,
      processingType: ProcessingType = Streaming,
      json: Option[DisplayableProcess] = None,
      lastAction: Option[ProcessActionType] = None,
      description: Option[String] = None,
      history: Option[List[ScenarioVersion]] = None
  ): ScenarioWithDetailsEntity[DisplayableProcess] = {
    val jsonData = json
      .getOrElse(createEmptyJson(processingType))
    repository.ScenarioWithDetailsEntity[DisplayableProcess](
      name = name,
      processId = ProcessId(generateId()),
      processVersionId = VersionId.initialVersionId,
      isLatestVersion = true,
      description = description,
      isArchived = isArchived,
      isFragment = isFragment,
      processingType = processingType,
      processCategory = category,
      modificationDate = Instant.now(),
      modifiedAt = Instant.now(),
      modifiedBy = "user1",
      createdAt = Instant.now(),
      createdBy = "user1",
      tags = None,
      lastAction = lastAction.map(createProcessAction),
      lastStateAction = lastAction.collect {
        case action if ProcessActionType.StateActionsTypes.contains(action) => createProcessAction(action)
      },
      lastDeployedAction = lastAction.collect { case Deploy =>
        createProcessAction(Deploy)
      },
      json = jsonData,
      history = history,
      modelVersion = None
    )
  }

  private def createEmptyJson(processingType: ProcessingType) = {
    val typeSpecificProperties = processingType match {
      case Streaming | Fraud => StreamMetaData()
      case _                 => throw new IllegalArgumentException(s"Unknown processing type: $processingType.")
    }

    DisplayableProcess(ProcessProperties(typeSpecificProperties), Nil, Nil)
  }

  lazy val sampleDisplayableFragment: DisplayableProcess =
    createDisplayableFragment(
      List(FragmentInputDefinition("input", List(FragmentParameter("in", FragmentClazzRef[String]))))
    )

  def createDisplayableFragment(
      nodes: List[NodeData]
  ): DisplayableProcess =
    DisplayableProcess(ProcessProperties(FragmentSpecificData()), nodes, Nil)

  def createProcessAction(action: ProcessActionType): ProcessAction = ProcessAction(
    id = ProcessActionId(UUID.randomUUID()),
    processId = ProcessId(generateId()),
    processVersionId = VersionId(generateId()),
    createdAt = Instant.now(),
    performedAt = Instant.now(),
    user = "user",
    actionType = action,
    state = ProcessActionState.Finished,
    failureMessage = None,
    commentId = None,
    comment = None,
    buildInfo = Map.empty
  )

  private def generateId() = Math.abs(randomGenerator.nextLong())

}
