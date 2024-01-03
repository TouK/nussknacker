package pl.touk.nussknacker.ui.api.helpers

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, ProcessingType, ScenarioVersion, VersionId}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, RequestResponseMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, NodeData}
import pl.touk.nussknacker.restmodel.scenariodetails._
import pl.touk.nussknacker.restmodel.validation.ValidatedDisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, RequestResponse, Streaming}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.{ScenarioWithDetailsConversions, repository}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

import java.time.Instant
import java.util.UUID
import scala.util.Random

object TestProcessUtil {

  private val randomGenerator = new Random()

  def toDisplayable(
      espProcess: CanonicalProcess,
      processingType: ProcessingType = TestProcessingTypes.Streaming,
      category: Category = TestCategories.Category1
  ): DisplayableProcess =
    ProcessConverter.toDisplayable(espProcess, processingType, category)

  def toCanonical(displayable: DisplayableProcess): CanonicalProcess =
    ProcessConverter.fromDisplayable(displayable)

  def toJson(espProcess: CanonicalProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): Json =
    Encoder[DisplayableProcess].apply(toDisplayable(espProcess, processingType))

  def createBasicProcess(
      name: String,
      category: Category,
      isArchived: Boolean = false,
      processingType: String = Streaming,
      lastAction: Option[ProcessActionType] = None,
      json: Option[DisplayableProcess] = None
  ): ScenarioWithDetailsEntity[DisplayableProcess] =
    toDetails(
      ProcessName(name),
      category,
      isFragment = false,
      isArchived,
      processingType,
      json = json,
      lastAction = lastAction
    )

  def createFragment(
      name: String,
      category: Category,
      isArchived: Boolean = false,
      processingType: String = Streaming,
      json: Option[DisplayableProcess] = None,
      lastAction: Option[ProcessActionType] = None
  ): ScenarioWithDetailsEntity[DisplayableProcess] = {
    val processName = ProcessName(name)
    toDetails(
      processName,
      category,
      isFragment = true,
      isArchived,
      processingType,
      lastAction = lastAction,
      json = Some(json.getOrElse(createDisplayableFragment(processName, processingType, category)))
    )
  }

  def displayableToProcess(
      displayable: DisplayableProcess,
      category: Category = TestCategories.Category1,
      isArchived: Boolean = false,
      isFragment: Boolean = false
  ): ScenarioWithDetailsEntity[DisplayableProcess] =
    toDetails(
      displayable.name,
      category,
      isArchived = isArchived,
      processingType = displayable.processingType,
      json = Some(displayable),
      isFragment = isFragment
    )

  def wrapWithDetails(
      displayable: DisplayableProcess,
      validationResult: ValidationResult = ValidationResult.success
  ): ScenarioWithDetails = {
    ScenarioWithDetailsConversions.fromEntity(
      toDetails(
        displayable.name,
        processingType = displayable.processingType,
        category = displayable.category
      ).copy(json = ValidatedDisplayableProcess.withValidationResult(displayable, validationResult))
    )
  }

  def toDetails(
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
      .map(_.copy(name = name, processingType = processingType, category = category))
      .getOrElse(createEmptyJson(name, processingType, category))
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

  private def createEmptyJson(name: ProcessName, processingType: ProcessingType, category: Category) = {
    val typeSpecificProperties = processingType match {
      case RequestResponse   => RequestResponseMetaData(None)
      case Streaming | Fraud => StreamMetaData()
      case _                 => throw new IllegalArgumentException(s"Unknown processing type: $processingType.")
    }

    DisplayableProcess(name, ProcessProperties(typeSpecificProperties), Nil, Nil, processingType, category)
  }

  def createDisplayableFragment(
      name: ProcessName,
      processingType: ProcessingType,
      category: Category
  ): DisplayableProcess =
    createDisplayableFragment(
      name,
      List(FragmentInputDefinition("input", List(FragmentParameter("in", FragmentClazzRef[String])))),
      processingType,
      category
    )

  def createDisplayableFragment(
      name: ProcessName,
      nodes: List[NodeData],
      processingType: ProcessingType,
      category: Category
  ): DisplayableProcess =
    DisplayableProcess(name, ProcessProperties(FragmentSpecificData()), nodes, Nil, processingType, category)

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
