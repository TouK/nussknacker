package pl.touk.nussknacker.ui.api.helpers

import io.circe.{Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType.ProcessActionType
import pl.touk.nussknacker.engine.api.process.{ProcessId, VersionId}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.restmodel.ProcessType
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, ProcessAction}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes.Streaming
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter

import java.time.LocalDateTime
import scala.util.Random

object TestProcessUtil {

  type ProcessWithoutJson = BaseProcessDetails[Unit]

  private val randomGenerator = new Random()

  def toDisplayable(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): DisplayableProcess = {
    ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), processingType)
  }

  def toJson(espProcess: EspProcess, processingType: ProcessingType = TestProcessingTypes.Streaming): Json = {
    Encoder[DisplayableProcess].apply(toDisplayable(espProcess, processingType))
  }

  def createBasicProcess(name: String, isSubprocess: Boolean, isArchived: Boolean, category: String,
                         processingType: String = Streaming, processType: ProcessType = ProcessType.Graph,
                         lastAction: Option[ProcessActionType] = None): BaseProcessDetails[Unit] = {
    BaseProcessDetails[Unit](
      id = name,
      name = name,
      processId = ProcessId(generateId()),
      processVersionId = 1,
      isLatestVersion = true,
      description = None,
      isArchived = isArchived,
      isSubprocess = isSubprocess,
      processType = processType,
      processingType = processingType,
      processCategory = category,
      modificationDate = LocalDateTime.now(),
      createdAt = LocalDateTime.now(),
      createdBy = "user",
      tags = List(),
      lastAction = lastAction.map(createProcessAction),
      lastDeployedAction = None,
      json = None,
      history = List(),
      modelVersion = None
    )
  }

  def createProcessAction(action: ProcessActionType): ProcessAction = ProcessAction(
    processVersionId = VersionId(generateId()),
    performedAt = LocalDateTime.now(),
    user = "user",
    action = action,
    commentId = None,
    comment = None,
    buildInfo = Map.empty
  )

  private def generateId() = Math.abs(randomGenerator.nextLong())
}
