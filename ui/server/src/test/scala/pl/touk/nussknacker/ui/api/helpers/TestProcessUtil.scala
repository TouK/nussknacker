package pl.touk.nussknacker.ui.api.helpers

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.FragmentSpecificData
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.{Deploy, ProcessActionType}
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node.{NodeData, SubprocessInputDefinition}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessAction, ProcessDetails, ProcessVersion, ValidatedProcessDetails}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.Streaming
import pl.touk.nussknacker.ui.process.ProcessCategoryService.Category
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import java.time.LocalDateTime
import scala.util.Random

object TestProcessUtil {

  type ProcessWithJson = BaseProcessDetails[DisplayableProcess]

  private val randomGenerator = new Random()

  def toDisplayable(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): DisplayableProcess =
    ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), processingType)

  def toJson(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): Json =
    Encoder[DisplayableProcess].apply(toDisplayable(espProcess, processingType))

  def createBasicProcess(name: String, category: Category, isArchived: Boolean = false, processingType: String = Streaming, lastAction: Option[ProcessActionType] = None, json: Option[DisplayableProcess] = None): BaseProcessDetails[DisplayableProcess] =
    toDetails(name, category, isSubprocess = false, isArchived, processingType, json = json, lastAction = lastAction)

  def createSubProcess(name: String, category: Category, isArchived: Boolean = false, processingType: String = Streaming, json: Option[DisplayableProcess] = None, lastAction: Option[ProcessActionType] = None): BaseProcessDetails[DisplayableProcess] =
    toDetails(name, category, isSubprocess = true, isArchived, processingType, lastAction = lastAction, json = Some(json.getOrElse(createDisplayableSubprocess(name, processingType))))

  def displayableToProcess(displayable: DisplayableProcess, category: Category = TestCategories.Category1, isArchived: Boolean = false) : ProcessDetails =
    toDetails(displayable.id, category, isArchived = isArchived, processingType = displayable.processingType, json = Some(displayable))

  def validatedToProcess(displayable: ValidatedDisplayableProcess) : ValidatedProcessDetails =
    toDetails(displayable.id, processingType = displayable.processingType).copy(json = Some(displayable))

  def toDetails(name: String, category: Category = TestCategories.Category1, isSubprocess: Boolean = false, isArchived: Boolean = false,
                processingType: ProcessingType = Streaming, json: Option[DisplayableProcess] = None, lastAction: Option[ProcessActionType] = None,
                description: Option[String] = None, history: Option[List[ProcessVersion]] = None) : ProcessDetails =
    BaseProcessDetails[DisplayableProcess](
      id = name,
      name = name,
      processId = ProcessId(generateId()),
      processVersionId = VersionId(1),
      isLatestVersion = true,
      description = description,
      isArchived = isArchived,
      isSubprocess = isSubprocess,
      processingType = processingType,
      processCategory = category,
      modificationDate = LocalDateTime.now(),
      modifiedAt = LocalDateTime.now(),
      modifiedBy = "user1",
      createdAt = LocalDateTime.now(),
      createdBy = "user1",
      tags = List(),
      lastAction = lastAction.map(createProcessAction),
      lastDeployedAction = lastAction.collect {
        case Deploy => createProcessAction(Deploy)
      },
      json = json.map(_.copy(id = name, processingType = processingType)),
      history = history.getOrElse(Nil),
      modelVersion = None
    )

  def createDisplayableSubprocess(name: String, processingType: ProcessingType): DisplayableProcess =
    createDisplayableSubprocess(name, List(SubprocessInputDefinition("input", List(SubprocessParameter("in", SubprocessClazzRef[String])))), processingType)

  def createDisplayableSubprocess(name: String, nodes: List[NodeData], processingType: ProcessingType): DisplayableProcess =
    DisplayableProcess(name, ProcessProperties(FragmentSpecificData()), nodes, Nil, processingType)

  def createProcessAction(action: ProcessActionType): ProcessAction = ProcessAction(
    processVersionId = VersionId(generateId()),
    performedAt = LocalDateTime.now(),
    user = "user",
    action = action,
    commentId = None,
    comment = None,
    buildInfo = Map.empty
  )

  def createEmptyStreamingGraph(id: String): GraphProcess = GraphProcess(
    s"""
       |{
       |  "metaData" : {
       |    "id" : "$id",
       |    "typeSpecificData" : {
       |      "type" : "StreamMetaData"
       |    }
       |  },
       |  "nodes" : []
       |}
       |""".stripMargin
  )

  private def generateId() = Math.abs(randomGenerator.nextLong())

}
