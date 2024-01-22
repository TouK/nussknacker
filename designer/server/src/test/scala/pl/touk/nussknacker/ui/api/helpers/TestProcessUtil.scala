package pl.touk.nussknacker.ui.api.helpers

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.{ProcessAction, ProcessActionId, ProcessActionState, ProcessActionType}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Cancel, Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, RequestResponseMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{FragmentInputDefinition, NodeData}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.{Fraud, RequestResponse, Streaming}
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

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
  ): BaseProcessDetails[DisplayableProcess] =
    toDetails(name, category, isFragment = false, isArchived, processingType, json = json, lastAction = lastAction)

  def createFragment(
      name: String,
      category: Category,
      isArchived: Boolean = false,
      processingType: String = Streaming,
      json: Option[DisplayableProcess] = None,
      lastAction: Option[ProcessActionType] = None
  ): BaseProcessDetails[DisplayableProcess] =
    toDetails(
      name,
      category,
      isFragment = true,
      isArchived,
      processingType,
      lastAction = lastAction,
      json = Some(json.getOrElse(createDisplayableFragment(name, processingType, category)))
    )

  def displayableToProcess(
      displayable: DisplayableProcess,
      category: Category = TestCategories.Category1,
      isArchived: Boolean = false,
      isFragment: Boolean = false
  ): ProcessDetails =
    toDetails(
      displayable.id,
      category,
      isArchived = isArchived,
      processingType = displayable.processingType,
      json = Some(displayable),
      isFragment = isFragment
    )

  def validatedToProcess(displayable: ValidatedDisplayableProcess): ValidatedProcessDetails =
    toDetails(
      displayable.id,
      processingType = displayable.processingType,
      category = displayable.category
    ).copy(json = displayable)

  def toDetails(
      name: String,
      category: Category = TestCategories.Category1,
      isFragment: Boolean = false,
      isArchived: Boolean = false,
      processingType: ProcessingType = Streaming,
      json: Option[DisplayableProcess] = None,
      lastAction: Option[ProcessActionType] = None,
      description: Option[String] = None,
      history: Option[List[ProcessVersion]] = None
  ): ProcessDetails = {
    val jsonData = json
      .map(_.copy(id = name, processingType = processingType, category = category))
      .getOrElse(createEmptyJson(name, processingType, category))
    BaseProcessDetails[DisplayableProcess](
      id = name,
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
      tags = Some(List()),
      lastAction = lastAction.map(createProcessAction),
      lastStateAction = lastAction.collect {
        case action if StateActionsTypes.contains(action) => createProcessAction(action)
      },
      lastDeployedAction = lastAction.collect { case Deploy =>
        createProcessAction(Deploy)
      },
      json = jsonData,
      history = history,
      modelVersion = None
    )
  }

  private def createEmptyJson(id: String, processingType: ProcessingType, category: Category) = {
    val typeSpecificProperties = processingType match {
      case RequestResponse   => RequestResponseMetaData(None)
      case Streaming | Fraud => StreamMetaData()
      case _                 => throw new IllegalArgumentException(s"Unknown processing type: $processingType.")
    }

    DisplayableProcess(id, ProcessProperties(typeSpecificProperties), Nil, Nil, processingType, category)
  }

  def createDisplayableFragment(name: String, processingType: ProcessingType, category: Category): DisplayableProcess =
    createDisplayableFragment(
      name,
      List(FragmentInputDefinition("input", List(FragmentParameter("in", FragmentClazzRef[String])))),
      processingType,
      category
    )

  def createDisplayableFragment(
      name: String,
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
