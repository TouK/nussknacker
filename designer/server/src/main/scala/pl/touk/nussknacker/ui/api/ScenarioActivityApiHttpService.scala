package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Comment
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, _}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.description.scenarioActivity.{Dtos, Endpoints}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.{Comment => _, _}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError.{
  InvalidComment,
  NoActivity,
  NoAttachment,
  NoComment,
  NoPermission,
  NoScenario
}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioAttachmentService}
import pl.touk.nussknacker.ui.process.ProcessService.GetScenarioWithDetailsOptions
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, DeploymentComment}
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository.{
  CommentModificationMetadata,
  DeleteAttachmentError,
  ModifyCommentError
}
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.server.HeadersSupport.ContentDisposition
import pl.touk.nussknacker.ui.server.TapirStreamEndpointProvider
import sttp.model.MediaType

import java.io.ByteArrayInputStream
import java.net.URLConnection
import java.time.ZoneId
import scala.concurrent.{ExecutionContext, Future}

class ScenarioActivityApiHttpService(
    authManager: AuthManager,
    fetchScenarioActivityService: FetchScenarioActivityService,
    scenarioActivityRepository: ScenarioActivityRepository,
    scenarioService: ProcessService,
    scenarioAuthorizer: AuthorizeProcess,
    attachmentService: ScenarioAttachmentService,
    deploymentCommentSettings: Option[DeploymentCommentSettings],
    streamEndpointProvider: TapirStreamEndpointProvider,
    dbioActionRunner: DBIOActionRunner,
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private implicit val zoneId: ZoneId = ZoneId.systemDefault()

  private val securityInput = authManager.authenticationEndpointInput()

  private val endpoints = new Endpoints(securityInput, streamEndpointProvider, zoneId)

  expose {
    endpoints.deprecatedScenarioActivityEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => scenarioName: ProcessName =>
        for {
          scenarioId      <- getScenarioIdByName(scenarioName)
          _               <- isAuthorized(scenarioId, Permission.Read)
          processActivity <- fetchProcessActivity(scenarioId)
        } yield processActivity
      }
  }

  expose {
    endpoints.deprecatedAddCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: AddCommentRequest =>
        for {
          scenarioId <- getScenarioIdByName(request.scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          _          <- addNewComment(request, scenarioId)
        } yield ()
      }
  }

  expose {
    endpoints.deprecatedDeleteCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: DeprecatedDeleteCommentRequest =>
        for {
          scenarioId <- getScenarioIdByName(request.scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          _          <- deleteComment(request, scenarioId)
        } yield ()
      }
  }

  expose {
    endpoints.addAttachmentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: AddAttachmentRequest =>
        for {
          scenarioId <- getScenarioIdByName(request.scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          _          <- saveAttachment(request, scenarioId)
        } yield ()
      }
  }

  expose {
    endpoints.deleteAttachmentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: DeleteAttachmentRequest =>
        for {
          scenarioId <- getScenarioIdByName(request.scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          _          <- markAttachmentAsDeleted(request, scenarioId)
        } yield ()
      }
  }

  expose {
    endpoints.downloadAttachmentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: GetAttachmentRequest =>
        for {
          scenarioId      <- getScenarioIdByName(request.scenarioName)
          _               <- isAuthorized(scenarioId, Permission.Read)
          maybeAttachment <- EitherT.right(attachmentService.readAttachment(request.attachmentId, scenarioId))
          response = buildResponse(maybeAttachment)
        } yield response
      }
  }

  expose {
    endpoints.scenarioActivitiesEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => scenarioName: ProcessName =>
        for {
          scenarioId <- getScenarioIdByName(scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Read)
          activities <- fetchActivities(ProcessIdWithName(scenarioId, scenarioName))
        } yield ScenarioActivities(activities)
      }
  }

  expose {
    endpoints.attachmentsEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => processName: ProcessName =>
        for {
          scenarioId  <- getScenarioIdByName(processName)
          _           <- isAuthorized(scenarioId, Permission.Read)
          attachments <- fetchAttachments(scenarioId)
        } yield attachments
      }
  }

  expose {
    endpoints.scenarioActivitiesMetadataEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => scenarioName: ProcessName =>
        for {
          scenarioId <- getScenarioIdByName(scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Read)
          scenarioWithDetails <- EitherT.right(
            scenarioService.getLatestProcessWithDetails(
              ProcessIdWithName(scenarioId, scenarioName),
              GetScenarioWithDetailsOptions.detailsOnly
            )
          )
          scenarioType = if (scenarioWithDetails.isFragment) ScenarioType.Fragment else ScenarioType.Scenario
          metadata     = ScenarioActivitiesMetadata.default(scenarioType)
        } yield metadata
      }
  }

  expose {
    endpoints.addCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: AddCommentRequest =>
        for {
          scenarioId <- getScenarioIdByName(request.scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          _          <- addNewComment(request, scenarioId)
        } yield ()
      }
  }

  expose {
    endpoints.editCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: EditCommentRequest =>
        for {
          scenarioId <- getScenarioIdByName(request.scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          _          <- editComment(request, scenarioId)
        } yield ()
      }
  }

  expose {
    endpoints.deleteCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => request: DeleteCommentRequest =>
        for {
          scenarioId <- getScenarioIdByName(request.scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          _          <- deleteComment(request, scenarioId)
        } yield ()
      }
  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    EitherT.fromOptionF(
      scenarioService.getProcessId(scenarioName),
      NoScenario(scenarioName)
    )
  }

  private def isAuthorized(scenarioId: ProcessId, permission: Permission)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      scenarioAuthorizer
        .check(scenarioId, permission, loggedUser)
        .map[Either[ScenarioActivityError, Unit]] {
          case true  => Right(())
          case false => Left(NoPermission)
        }
    )

  private def fetchProcessActivity(
      scenarioId: ProcessId
  ): EitherT[Future, ScenarioActivityError, Legacy.ProcessActivity] =
    EitherT
      .right(
        dbioActionRunner.run(
          scenarioActivityRepository.findActivity(scenarioId)
        )
      )

  private def fetchActivities(
      processIdWithName: ProcessIdWithName
  )(implicit loggedUser: LoggedUser): EitherT[Future, ScenarioActivityError, List[Dtos.ScenarioActivity]] = {
    EitherT.right {
      for {
        combinedActivities <- fetchScenarioActivityService.fetchActivities(processIdWithName, after = None)
        //  The API endpoint returning scenario activities does not yet have support for filtering. We made a decision to:
        //  - for activities not related to deployments:        always display them on FE
        //  - for activities related to batch deployments:      always display them on FE
        //  - for activities related to non-batch deployments:  display on FE only those, that represent successful operations
        combinedSuccessfulActivities = combinedActivities.filter {
          case _: BatchDeploymentRelatedActivity => true
          case activity: DeploymentRelatedActivity =>
            activity.result match {
              case _: DeploymentResult.Success => true
              case _: DeploymentResult.Failure => false
            }
          case _ => true
        }
        sortedResult = combinedSuccessfulActivities.map(toDto).toList.sortBy(_.date)
      } yield sortedResult
    }
  }

  private def toDto(scenarioComment: ScenarioComment): Dtos.ScenarioActivityComment = scenarioComment match {
    case ScenarioComment.WithContent(comment, _, _) =>
      Dtos.ScenarioActivityComment(
        content = Dtos.ScenarioActivityCommentContent.Available(comment.content),
        lastModifiedBy = scenarioComment.lastModifiedByUserName.value,
        lastModifiedAt = scenarioComment.lastModifiedAt,
      )
    case ScenarioComment.WithoutContent(_, _) =>
      Dtos.ScenarioActivityComment(
        content = Dtos.ScenarioActivityCommentContent.NotAvailable,
        lastModifiedBy = scenarioComment.lastModifiedByUserName.value,
        lastModifiedAt = scenarioComment.lastModifiedAt,
      )
  }

  private def toDto(attachment: ScenarioAttachment): Dtos.ScenarioActivityAttachment = {
    attachment match {
      case ScenarioAttachment.Available(attachmentId, attachmentFilename, lastModifiedByUserName, lastModifiedAt) =>
        Dtos.ScenarioActivityAttachment(
          file = Dtos.ScenarioActivityAttachmentFile.Available(attachmentId.value),
          filename = attachmentFilename.value,
          lastModifiedBy = lastModifiedByUserName.value,
          lastModifiedAt = lastModifiedAt,
        )
      case ScenarioAttachment.Deleted(attachmentFilename, deletedByUserName, deletedAt) =>
        Dtos.ScenarioActivityAttachment(
          file = Dtos.ScenarioActivityAttachmentFile.Deleted,
          filename = attachmentFilename.value,
          lastModifiedBy = deletedByUserName.value,
          lastModifiedAt = deletedAt,
        )
    }
  }

  private def toDto(scenarioActivity: ScenarioActivity): Dtos.ScenarioActivity = {
    scenarioActivity match {
      case ScenarioActivity.ScenarioCreated(_, scenarioActivityId, user, date, scenarioVersionId) =>
        Dtos.ScenarioActivity.forScenarioCreated(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value)
        )
      case ScenarioActivity.ScenarioArchived(_, scenarioActivityId, user, date, scenarioVersionId) =>
        Dtos.ScenarioActivity.forScenarioArchived(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value)
        )
      case ScenarioActivity.ScenarioUnarchived(_, scenarioActivityId, user, date, scenarioVersionId) =>
        Dtos.ScenarioActivity.forScenarioUnarchived(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value)
        )
      case ScenarioActivity.ScenarioDeployed(_, scenarioActivityId, user, date, scenarioVersionId, comment, _) =>
        Dtos.ScenarioActivity.forScenarioDeployed(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioPaused(_, scenarioActivityId, user, date, scenarioVersionId, comment, _) =>
        Dtos.ScenarioActivity.forScenarioPaused(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioCanceled(_, scenarioActivityId, user, date, scenarioVersionId, comment, _) =>
        Dtos.ScenarioActivity.forScenarioCanceled(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioModified(_, scenarioActivityId, user, date, oldVersionId, newVersionId, comment) =>
        Dtos.ScenarioActivity.forScenarioModified(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          previousScenarioVersionId = oldVersionId.map(_.value),
          scenarioVersionId = newVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioNameChanged(_, id, user, date, version, oldName, newName) =>
        Dtos.ScenarioActivity.forScenarioNameChanged(
          id = id.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = version.map(_.value),
          oldName = oldName,
          newName = newName,
        )
      case ScenarioActivity.CommentAdded(_, scenarioActivityId, user, date, scenarioVersionId, comment) =>
        Dtos.ScenarioActivity.forCommentAdded(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.AttachmentAdded(_, scenarioActivityId, user, date, scenarioVersionId, attachment) =>
        Dtos.ScenarioActivity.forAttachmentAdded(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          attachment = toDto(attachment),
        )
      case ScenarioActivity.ChangedProcessingMode(_, scenarioActivityId, user, date, scenarioVersionId, from, to) =>
        Dtos.ScenarioActivity.forChangedProcessingMode(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          from = from.entryName,
          to = to.entryName
        )
      case ScenarioActivity.IncomingMigration(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            sourceEnvironment,
            sourceUser,
            sourceScenarioVersionId,
            targetEnvironment,
          ) =>
        Dtos.ScenarioActivity.forIncomingMigration(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          sourceEnvironment = sourceEnvironment.name,
          sourceUser = sourceUser.value,
          sourceScenarioVersionId = sourceScenarioVersionId.map(_.value),
          targetEnvironment = targetEnvironment.map(_.name),
        )
      case ScenarioActivity.OutgoingMigration(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            destinationEnvironment
          ) =>
        Dtos.ScenarioActivity.forOutgoingMigration(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          destinationEnvironment = destinationEnvironment.name,
        )
      case ScenarioActivity.PerformedSingleExecution(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            comment,
            result,
          ) =>
        Dtos.ScenarioActivity.forPerformedSingleExecution(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
          dateFinished = result.dateFinished,
          errorMessage = result match {
            case DeploymentResult.Success(_)               => None
            case DeploymentResult.Failure(_, errorMessage) => errorMessage
          },
        )
      case ScenarioActivity.PerformedScheduledExecution(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            scheduledExecutionStatus,
            dateFinished,
            scheduleName,
            createdAt,
            nextRetryAt,
            retriesLeft,
          ) =>
        Dtos.ScenarioActivity.forPerformedScheduledExecution(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          dateFinished = dateFinished,
          scheduleName = scheduleName,
          scheduledExecutionStatus = scheduledExecutionStatus,
          createdAt = createdAt,
          retriesLeft = retriesLeft,
          nextRetryAt = nextRetryAt,
        )
      case ScenarioActivity.AutomaticUpdate(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            changes,
          ) =>
        Dtos.ScenarioActivity.forAutomaticUpdate(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          changes = changes,
        )
      case ScenarioActivity.CustomAction(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            actionName,
            comment,
            result,
          ) =>
        Dtos.ScenarioActivity.forCustomAction(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          actionName = actionName,
          comment = toDto(comment),
          customIcon = None,
          errorMessage = result match {
            case DeploymentResult.Success(_)               => None
            case DeploymentResult.Failure(_, errorMessage) => errorMessage
          },
        )
    }
  }

  private def addNewComment(request: AddCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, ScenarioActivityId] =
    EitherT.right(
      dbioActionRunner.run(
        scenarioActivityRepository.addComment(scenarioId, request.versionId, request.commentContent)
      )
    )

  private def editComment(request: EditCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, ScenarioActivityId] =
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.editComment(
          scenarioId,
          ScenarioActivityId(request.scenarioActivityId),
          validateComment(_, request.commentContent),
          request.commentContent,
        )
      )
    ).leftMap {
      case ModifyCommentError.InvalidContent(error) =>
        InvalidComment(error)
      case ModifyCommentError.ActivityDoesNotExist | ModifyCommentError.CommentDoesNotExist |
          ModifyCommentError.CouldNotModifyComment =>
        NoActivity(request.scenarioActivityId)
    }

  private def validateComment(commentModificationMetadata: CommentModificationMetadata, content: String) = {
    val commentOpt = Comment.from(content)
    val result = if (commentModificationMetadata.commentForScenarioDeployed) {
      DeploymentComment.createDeploymentComment(commentOpt, deploymentCommentSettings).toEither match {
        case Right(commentOpt) => Right(commentOpt)
        case Left(error)       => Left(ModifyCommentError.InvalidContent(error.message))
      }
    } else {
      Right(commentOpt)
    }
    result.flatMap {
      case Some(_) => Right(())
      case None    => Left(ModifyCommentError.InvalidContent("Empty comment"))
    }
  }

  private def deleteComment(request: DeprecatedDeleteCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, ScenarioActivityId] = {
    DeploymentComment.createDeploymentComment(None, deploymentCommentSettings).toEither
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.deleteComment(
          scenarioId,
          request.commentId,
          validateCommentCanBeRemoved(_),
        )
      )
    ).leftMap {
      case ModifyCommentError.InvalidContent(error) =>
        InvalidComment(error)
      case ModifyCommentError.ActivityDoesNotExist | ModifyCommentError.CommentDoesNotExist |
          ModifyCommentError.CouldNotModifyComment =>
        NoComment(request.commentId)
    }
  }

  private def deleteComment(request: DeleteCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, ScenarioActivityId] =
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.deleteComment(
          scenarioId,
          ScenarioActivityId(request.scenarioActivityId),
          validateCommentCanBeRemoved(_),
        )
      )
    ).leftMap {
      case ModifyCommentError.InvalidContent(error) =>
        InvalidComment(error)
      case ModifyCommentError.ActivityDoesNotExist | ModifyCommentError.CommentDoesNotExist |
          ModifyCommentError.CouldNotModifyComment =>
        NoActivity(request.scenarioActivityId)
    }

  private def validateCommentCanBeRemoved(commentModificationMetadata: CommentModificationMetadata) = {
    if (commentModificationMetadata.commentForScenarioDeployed) {
      DeploymentComment.createDeploymentComment(None, deploymentCommentSettings).toEither match {
        case Right(_)    => Right(())
        case Left(error) => Left(ModifyCommentError.InvalidContent(error.message))
      }
    } else {
      Right(())
    }
  }

  private def fetchAttachments(scenarioId: ProcessId): EitherT[Future, ScenarioActivityError, ScenarioAttachments] = {
    EitherT
      .right(
        dbioActionRunner.run(scenarioActivityRepository.findAttachments(scenarioId))
      )
      .map(_.map { attachmentEntity =>
        Attachment(
          id = attachmentEntity.id,
          scenarioVersion = attachmentEntity.processVersionId.value,
          fileName = attachmentEntity.fileName,
          user = attachmentEntity.user,
          createDate = attachmentEntity.createDateTime,
        )
      }.toList)
      .map(ScenarioAttachments.apply)
  }

  private def saveAttachment(request: AddAttachmentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] = {
    EitherT.right(
      attachmentService.saveAttachment(scenarioId, request.versionId, request.fileName.value, request.body)
    )
  }

  private def markAttachmentAsDeleted(request: DeleteAttachmentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.markAttachmentAsDeleted(scenarioId, request.attachmentId)
      )
    ).leftMap { case DeleteAttachmentError.CouldNotDeleteAttachment => NoAttachment(request.attachmentId) }

  private def buildResponse(maybeAttachment: Option[(String, Array[Byte])]): GetAttachmentResponse =
    maybeAttachment match {
      case Some((fileName, content)) =>
        GetAttachmentResponse(
          inputStream = new ByteArrayInputStream(content),
          fileName = ContentDisposition.fromFileNameString(fileName).headerValue(),
          contentType = Option(URLConnection.guessContentTypeFromName(fileName))
            .getOrElse(MediaType.ApplicationOctetStream.toString())
        )
      case None => GetAttachmentResponse.emptyResponse
    }

}
