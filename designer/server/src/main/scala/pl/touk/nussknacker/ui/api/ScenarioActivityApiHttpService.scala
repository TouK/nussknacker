package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.{
  ScenarioActivity,
  ScenarioActivityId,
  ScenarioAttachment,
  ScenarioComment
}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError.{
  NoActivity,
  NoComment,
  NoPermission,
  NoScenario
}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos._
import pl.touk.nussknacker.ui.api.description.scenarioActivity.{Dtos, Endpoints}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioAttachmentService}
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import pl.touk.nussknacker.ui.server.HeadersSupport.ContentDisposition
import pl.touk.nussknacker.ui.server.TapirStreamEndpointProvider
import sttp.model.MediaType

import java.io.ByteArrayInputStream
import java.net.URLConnection
import scala.concurrent.{ExecutionContext, Future}

class ScenarioActivityApiHttpService(
    authManager: AuthManager,
    scenarioActivityRepository: ScenarioActivityRepository,
    scenarioService: ProcessService,
    scenarioAuthorizer: AuthorizeProcess,
    attachmentService: ScenarioAttachmentService,
    streamEndpointProvider: TapirStreamEndpointProvider,
    dbioActionRunner: DBIOActionRunner,
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(authManager)
    with LazyLogging {

  private val securityInput = authManager.authenticationEndpointInput()

  private val endpoints = new Endpoints(securityInput, streamEndpointProvider)

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
          activities <- fetchActivities(scenarioId)
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
          metadata = ScenarioActivitiesMetadata.default
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
      scenarioId: ProcessId
  ): EitherT[Future, ScenarioActivityError, List[Dtos.ScenarioActivity]] =
    EitherT
      .right(
        dbioActionRunner.run(
          scenarioActivityRepository.findActivities(scenarioId)
        )
      )
      .map(_.map(toDto).toList)

  private def toDto(scenarioComment: ScenarioComment): Dtos.ScenarioActivityComment = {
    scenarioComment match {
      case ScenarioComment.Available(comment, lastModifiedByUserName, lastModifiedAt) =>
        Dtos.ScenarioActivityComment(
          content = Dtos.ScenarioActivityCommentContent.Available(comment),
          lastModifiedBy = lastModifiedByUserName.value,
          lastModifiedAt = lastModifiedAt,
        )
      case ScenarioComment.Deleted(deletedByUserName, deletedAt) =>
        Dtos.ScenarioActivityComment(
          content = Dtos.ScenarioActivityCommentContent.Deleted,
          lastModifiedBy = deletedByUserName.value,
          lastModifiedAt = deletedAt,
        )
    }
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
      case ScenarioActivity.ScenarioDeployed(_, scenarioActivityId, user, date, scenarioVersionId, comment) =>
        Dtos.ScenarioActivity.forScenarioDeployed(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioPaused(_, scenarioActivityId, user, date, scenarioVersionId, comment) =>
        Dtos.ScenarioActivity.forScenarioPaused(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioCanceled(_, scenarioActivityId, user, date, scenarioVersionId, comment) =>
        Dtos.ScenarioActivity.forScenarioCanceled(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioModified(_, scenarioActivityId, user, date, scenarioVersionId, comment) =>
        Dtos.ScenarioActivity.forScenarioModified(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
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
          targetEnvironment = targetEnvironment.name,
        )
      case ScenarioActivity.OutgoingMigration(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            comment,
            destinationEnvironment
          ) =>
        Dtos.ScenarioActivity.forOutgoingMigration(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
          destinationEnvironment = destinationEnvironment.name,
        )
      case ScenarioActivity.PerformedSingleExecution(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            comment,
            dateFinished,
            errorMessage
          ) =>
        Dtos.ScenarioActivity.forPerformedSingleExecution(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          comment = toDto(comment),
          dateFinished = dateFinished,
          errorMessage = errorMessage,
        )
      case ScenarioActivity.PerformedScheduledExecution(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            dateFinished,
            errorMessage
          ) =>
        Dtos.ScenarioActivity.forPerformedScheduledExecution(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          dateFinished = dateFinished,
          errorMessage = errorMessage,
        )
      case ScenarioActivity.AutomaticUpdate(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersionId,
            changes,
            errorMessage,
          ) =>
        Dtos.ScenarioActivity.forAutomaticUpdate(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          changes = changes,
          errorMessage = errorMessage,
        )
      case ScenarioActivity.CustomAction(_, scenarioActivityId, user, date, scenarioVersionId, actionName, comment) =>
        Dtos.ScenarioActivity.forCustomAction(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersionId = scenarioVersionId.map(_.value),
          actionName = actionName,
          comment = toDto(comment),
          customIcon = None,
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

  private def editComment(request: DeprecatedEditCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.editComment(scenarioId, request.commentId, request.commentContent)
      )
    ).leftMap(_ => NoComment(request.commentId))

  private def editComment(request: EditCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.editComment(
          scenarioId,
          ScenarioActivityId(request.scenarioActivityId),
          request.commentContent
        )
      )
    ).leftMap(_ => NoActivity(request.scenarioActivityId))

  private def deleteComment(request: DeprecatedDeleteCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      dbioActionRunner.run(scenarioActivityRepository.deleteComment(scenarioId, request.commentId))
    ).leftMap(_ => NoComment(request.commentId))

  private def deleteComment(request: DeleteCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.deleteComment(scenarioId, ScenarioActivityId(request.scenarioActivityId))
      )
    ).leftMap(_ => NoActivity(request.scenarioActivityId))

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
