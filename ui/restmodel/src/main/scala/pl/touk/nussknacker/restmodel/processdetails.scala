package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}

object processdetails {


  // todo: id -> ProcessName, name -> ProcessName
  case class BasicProcess(name: String,
                          processCategory: String,
                          processType: ProcessType,
                          processingType: ProcessingType,
                          isArchived:Boolean,
                          modificationDate: LocalDateTime,
                          currentlyDeployedAt: List[DeploymentEntry])

  // todo: name -> ProcessName
  case class BaseProcessDetails[ProcessShape](id: String,
                                              name: String,
                                              processVersionId: Long,
                                              isLatestVersion: Boolean,
                                              description: Option[String],
                                              isArchived:Boolean,
                                              processType: ProcessType,
                                              processingType: ProcessingType,
                                              processCategory: String,
                                              modificationDate: LocalDateTime,
                                              subprocessesModificationDate: Option[Map[String, LocalDateTime]],
                                              tags: List[String],
                                              currentlyDeployedAt: List[DeploymentEntry],
                                              json: Option[ProcessShape],
                                              history: List[ProcessHistoryEntry],
                                              modelVersion: Option[Int]) {
    def mapProcess[NewShape](action: ProcessShape => NewShape) : BaseProcessDetails[NewShape] = copy(json = json.map(action))

    def toBasicProcess: BasicProcess = BasicProcess(
      name = name,
      processCategory = processCategory,
      processType = processType,
      processingType = processingType,
      isArchived = isArchived,
      modificationDate = modificationDate,
      currentlyDeployedAt = currentlyDeployedAt
    )

    // todo: unsafe toLong; we need it for now - we use this class for both backend (id == real id) and frontend (id == name) purposes
    def idWithName: ProcessIdWithName = ProcessIdWithName(ProcessId(id.toLong), ProcessName(name))
  }

  type ProcessDetails = BaseProcessDetails[DisplayableProcess]

  type ValidatedProcessDetails = BaseProcessDetails[ValidatedDisplayableProcess]


  case class ProcessHistoryEntry(processId: String,
                                 processName: String,
                                 processVersionId: Long,
                                 createDate: LocalDateTime,
                                 user: String,
                                 deployments: List[DeploymentEntry]
                                )


  case class DeploymentEntry(processVersionId: Long, environment: String, deployedAt: LocalDateTime, user: String, buildInfo: Map[String, String])


}
