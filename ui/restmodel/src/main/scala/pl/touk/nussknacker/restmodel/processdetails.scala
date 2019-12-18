package pl.touk.nussknacker.restmodel

import java.time.LocalDateTime

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.generic.JsonCodec
import io.circe.java8.time.{JavaTimeDecoders, JavaTimeEncoders}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessId => ApiProcessId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.ProcessType.ProcessType
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.restmodel.processdetails.DeploymentAction.DeploymentAction

object processdetails extends JavaTimeEncoders with JavaTimeDecoders {

  object BasicProcess {
    def apply[ProcessShape](baseProcessDetails: BaseProcessDetails[ProcessShape]) = new BasicProcess(
      id = ApiProcessId(baseProcessDetails.id),
      name = ProcessName(baseProcessDetails.name),
      processVersionId = baseProcessDetails.processVersionId,
      isSubprocess = baseProcessDetails.isSubprocess,
      isArchived = baseProcessDetails.isArchived,
      processCategory = baseProcessDetails.processCategory,
      processType = baseProcessDetails.processType,
      processingType = baseProcessDetails.processingType,
      modificationDate = baseProcessDetails.modificationDate,
      createdAt = baseProcessDetails.createdAt,
      createdBy = baseProcessDetails.createdBy,
      deployment = baseProcessDetails.deployment
    )
  }

  @JsonCodec case class BasicProcess(id: ApiProcessId,
                                     name: ProcessName,
                                     processVersionId: Long,
                                     isArchived: Boolean,
                                     isSubprocess: Boolean,
                                     processCategory: String,
                                     processType: ProcessType,
                                     processingType: ProcessingType,
                                     modificationDate: LocalDateTime,
                                     createdAt: LocalDateTime,
                                     createdBy: String,
                                     deployment: Option[ProcessDeployment]) {
    def isDeployed: Boolean = deployment.exists(_.isDeployed)
    def isCanceled: Boolean = deployment.exists(_.isCanceled)
  }

  object BaseProcessDetails {
    implicit def encoder[T](implicit shape: Encoder[T]): Encoder[BaseProcessDetails[T]] = deriveEncoder
    implicit def decoder[T](implicit shape: Decoder[T]): Decoder[BaseProcessDetails[T]] = deriveDecoder
  }

  case class BaseProcessDetails[ProcessShape](id: String,
                                              name: String,
                                              processVersionId: Long,
                                              isLatestVersion: Boolean,
                                              description: Option[String],
                                              isArchived: Boolean,
                                              isSubprocess: Boolean,
                                              processType: ProcessType,
                                              processingType: ProcessingType,
                                              processCategory: String,
                                              modificationDate: LocalDateTime,
                                              createdAt: LocalDateTime,
                                              createdBy: String,
                                              tags: List[String],
                                              deployment: Option[ProcessDeployment],
                                              json: Option[ProcessShape],
                                              history: List[ProcessHistoryEntry],
                                              modelVersion: Option[Int]) {

    def isDeployed: Boolean = deployment.exists(_.isDeployed)
    def isCanceled: Boolean = deployment.exists(_.isCanceled)
    def mapProcess[NewShape](action: ProcessShape => NewShape) : BaseProcessDetails[NewShape] = copy(json = json.map(action))
    // todo: unsafe toLong; we need it for now - we use this class for both backend (id == real id) and frontend (id == name) purposes
    def idWithName: ProcessIdWithName = ProcessIdWithName(ProcessId(id.toLong), ProcessName(name))
  }

  // TODO we should split ProcessDetails and ProcessShape (json), than it won't be needed. Also BasicProcess won't be necessary than.
  sealed trait ProcessShapeFetchStrategy[ProcessShape]

  object ProcessShapeFetchStrategy {
    // TODO: Find places where FetchDisplayable is used and think about replacing it with FetchCanonical. We generally should use displayable representation
    //       only in GUI (not for deployment or exports) and in GUI it should be post processed using ProcessDictSubstitutor
    implicit case object FetchDisplayable extends ProcessShapeFetchStrategy[DisplayableProcess]
    implicit case object FetchCanonical extends ProcessShapeFetchStrategy[CanonicalProcess]
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
                                            deployments: List[ProcessDeployment])

  @JsonCodec case class DeploymentHistoryEntry(processVersionId: Long,
                                               time: LocalDateTime,
                                               user: String,
                                               deploymentAction: DeploymentAction,
                                               commentId: Option[Long],
                                               comment: Option[String],
                                               buildInfo: Map[String, String])

  @JsonCodec case class ProcessDeployment(processVersionId: Long,
                                          @Deprecated environment: String, //TODO: remove it in future..
                                          deployedAt: LocalDateTime,
                                          user: String,
                                          action: DeploymentAction,
                                          buildInfo: Map[String, String]) {
    def isDeployed: Boolean = action.equals(DeploymentAction.Deploy)
    def isCanceled: Boolean = action.equals(DeploymentAction.Cancel)
  }

  object DeploymentAction extends Enumeration {
    implicit val typeEncoder: Encoder[DeploymentAction.Value] = Encoder.enumEncoder(DeploymentAction)
    implicit val typeDecoder: Decoder[DeploymentAction.Value] = Decoder.enumDecoder(DeploymentAction)

    type DeploymentAction = Value
    val Deploy: Value = Value("DEPLOY")
    val Cancel: Value = Value("CANCEL")
  }
}
