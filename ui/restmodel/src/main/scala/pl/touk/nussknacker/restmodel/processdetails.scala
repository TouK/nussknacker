package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction.DeploymentAction

object processdetails {


  // todo: id -> ProcessName, name -> ProcessName
  case class BasicProcess(name: String,
                          processCategory: String,
                          processType: ProcessType,
                          processingType: ProcessingType,
                          isArchived:Boolean,
                          modificationDate: LocalDateTime,
                          currentDeployment: Option[DeploymentEntry],
                         //TODO: remove
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
                                              currentDeployment: Option[DeploymentEntry],
                                             //TODO: remove
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
      currentDeployment = currentlyDeployedAt.headOption,
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
                                //TODO: remove, replace with 'currentDeployments'
                                 deployments: List[DeploymentEntry]
                                )


  case class DeploymentEntry(processVersionId: Long,
                            //TODO: remove, in current usage it's not really needed
                             environment: String,
                             deployedAt: LocalDateTime,
                             user: String,
                             buildInfo: Map[String, String])

  case class DeploymentHistoryEntry(processVersionId: Long,
                             time: LocalDateTime,
                             user: String,
                             deploymentAction: DeploymentAction,
                             commentId: Option[Long],
                             buildInfo: Map[String, String])


  object DeploymentAction extends Enumeration {
    type DeploymentAction = Value
    val Deploy: Value = Value("DEPLOY")
    val Cancel: Value = Value("CANCEL")
  }



}
