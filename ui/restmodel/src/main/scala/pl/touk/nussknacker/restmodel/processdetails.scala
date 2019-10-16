package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.JsonCodec
import io.circe.java8.time.{JavaTimeDecoders, JavaTimeEncoders}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction.DeploymentAction

object processdetails extends JavaTimeEncoders with JavaTimeDecoders {


  // todo: id -> ProcessName, name -> ProcessName
  @JsonCodec case class BasicProcess(name: String,
                          processCategory: String,
                          processType: ProcessType,
                          processingType: ProcessingType,
                          isArchived:Boolean,
                          modificationDate: LocalDateTime,
                          currentDeployment: Option[DeploymentEntry],
                         //TODO: remove
                          currentlyDeployedAt: List[DeploymentEntry],
                          isSubprocess: Boolean)

  object BaseProcessDetails {

    implicit def encoder[T](implicit shape: Encoder[T]): Encoder[BaseProcessDetails[T]] = deriveEncoder

    implicit def decoder[T](implicit shape: Decoder[T]): Decoder[BaseProcessDetails[T]] = deriveDecoder

  }

  // todo: name -> ProcessName
  case class BaseProcessDetails[ProcessShape](id: String,
                                              name: String,
                                              processVersionId: Long,
                                              isLatestVersion: Boolean,
                                              description: Option[String],
                                              isArchived:Boolean,
                                              isSubprocess: Boolean,
                                              processType: ProcessType,
                                              processingType: ProcessingType,
                                              processCategory: String,
                                              modificationDate: LocalDateTime,
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
      currentlyDeployedAt = currentlyDeployedAt,
      isSubprocess = isSubprocess
    )

    // todo: unsafe toLong; we need it for now - we use this class for both backend (id == real id) and frontend (id == name) purposes
    def idWithName: ProcessIdWithName = ProcessIdWithName(ProcessId(id.toLong), ProcessName(name))
  }

  // TODO we should split ProcessDetails and ProcessShape (json), than it won't be needed. Also BasicProcess won't be necessary than.
  sealed trait ProcessShapeFetchStrategy[ProcessShape]

  object ProcessShapeFetchStrategy {
    implicit case object Fetch extends ProcessShapeFetchStrategy[DisplayableProcess]
    // In fact Unit won't be returned inside shape and Nothing would be more verbose but it won't help in compilation because Nothing <: DisplayableProcess
    implicit case object NotFetch extends ProcessShapeFetchStrategy[Unit]
  }

  type ProcessDetails = BaseProcessDetails[DisplayableProcess]

  type ValidatedProcessDetails = BaseProcessDetails[ValidatedDisplayableProcess]


  @JsonCodec case class ProcessHistoryEntry(processId: String,
                                            processName: String,
                                            processVersionId: Long,
                                            createDate: LocalDateTime,
                                            user: String,
                                            //TODO: remove, replace with 'currentDeployments'
                                            deployments: List[DeploymentEntry])


  @JsonCodec case class DeploymentEntry(processVersionId: Long,
                                        //TODO: remove, in current usage it's not really needed
                                        environment: String,
                                        deployedAt: LocalDateTime,
                                        user: String,
                                        buildInfo: Map[String, String])

  @JsonCodec case class DeploymentHistoryEntry(processVersionId: Long,
                                               time: LocalDateTime,
                                               user: String,
                                               deploymentAction: DeploymentAction,
                                               commentId: Option[Long],
                                               comment: Option[String],
                                               buildInfo: Map[String, String])


  object DeploymentAction extends Enumeration {

    implicit val typeEncoder: Encoder[DeploymentAction.Value] = Encoder.enumEncoder(DeploymentAction)
    implicit val typeDecoder: Decoder[DeploymentAction.Value] = Decoder.enumDecoder(DeploymentAction)

    type DeploymentAction = Value
    val Deploy: Value = Value("DEPLOY")
    val Cancel: Value = Value("CANCEL")
  }



}
