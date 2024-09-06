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
import java.time.Instant
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
    endpoints.scenarioActivitiesEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { implicit loggedUser => scenarioName: ProcessName =>
        for {
          scenarioId <- getScenarioIdByName(scenarioName)
          _          <- isAuthorized(scenarioId, Permission.Write)
          activities <- fetchActivities(scenarioId)
        } yield ScenarioActivities(activities)
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
          _          <- editComment(request)
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
          _          <- deleteComment(request)
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
      case ScenarioComment.Available(comment, lastModifiedByUserName) =>
        Dtos.ScenarioActivityComment(
          comment = Some(comment),
          lastModifiedBy = lastModifiedByUserName.value,
          lastModifiedAt = Instant.now(),
        )
      case ScenarioComment.Deleted(deletedByUserName) =>
        Dtos.ScenarioActivityComment(
          comment = None,
          lastModifiedBy = deletedByUserName.value,
          lastModifiedAt = Instant.now(),
        )
    }
  }

  private def toDto(attachment: ScenarioAttachment): Dtos.ScenarioActivityAttachment = {
    attachment match {
      case ScenarioAttachment.Available(attachmentId, attachmentFilename, lastModifiedByUserName) =>
        Dtos.ScenarioActivityAttachment(
          id = Some(attachmentId.value),
          filename = attachmentFilename.value,
          lastModifiedBy = lastModifiedByUserName.value,
          lastModifiedAt = Instant.now()
        )
      case ScenarioAttachment.Deleted(attachmentFilename, deletedByUserName) =>
        Dtos.ScenarioActivityAttachment(
          id = None,
          filename = attachmentFilename.value,
          lastModifiedBy = deletedByUserName.value,
          lastModifiedAt = Instant.now()
        )
    }
  }

  private def toDto(scenarioActivity: ScenarioActivity): Dtos.ScenarioActivity = {
    scenarioActivity match {
      case ScenarioActivity.ScenarioCreated(_, scenarioActivityId, user, date, scenarioVersion) =>
        Dtos.ScenarioActivity.ScenarioCreated(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value)
        )
      case ScenarioActivity.ScenarioArchived(_, scenarioActivityId, user, date, scenarioVersion) =>
        Dtos.ScenarioActivity.ScenarioArchived(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value)
        )
      case ScenarioActivity.ScenarioUnarchived(_, scenarioActivityId, user, date, scenarioVersion) =>
        Dtos.ScenarioActivity.ScenarioUnarchived(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value)
        )
      case ScenarioActivity.ScenarioDeployed(_, scenarioActivityId, user, date, scenarioVersion, comment) =>
        Dtos.ScenarioActivity.ScenarioDeployed(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioPaused(_, scenarioActivityId, user, date, scenarioVersion, comment) =>
        Dtos.ScenarioActivity.ScenarioPaused(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioCanceled(_, scenarioActivityId, user, date, scenarioVersion, comment) =>
        Dtos.ScenarioActivity.ScenarioCanceled(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioModified(_, scenarioActivityId, user, date, scenarioVersion, comment) =>
        Dtos.ScenarioActivity.ScenarioModified(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.ScenarioNameChanged(_, id, user, date, version, oldName, newName) =>
        Dtos.ScenarioActivity.ScenarioNameChanged(
          id = id.value,
          user = user.name.value,
          date = date,
          scenarioVersion = version.map(_.value),
          oldName = oldName,
          newName = newName,
        )
      case ScenarioActivity.CommentAdded(_, scenarioActivityId, user, date, scenarioVersion, comment) =>
        Dtos.ScenarioActivity.CommentAdded(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          comment = toDto(comment),
        )
      case ScenarioActivity.AttachmentAdded(_, scenarioActivityId, user, date, scenarioVersion, attachment) =>
        Dtos.ScenarioActivity.AttachmentAdded(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          attachment = toDto(attachment),
        )
      case ScenarioActivity.ChangedProcessingMode(_, scenarioActivityId, user, date, scenarioVersion, from, to) =>
        Dtos.ScenarioActivity.ChangedProcessingMode(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          from = from.entryName,
          to = to.entryName
        )
      case ScenarioActivity.IncomingMigration(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersion,
            sourceEnvironment,
            sourceScenarioVersion
          ) =>
        Dtos.ScenarioActivity.IncomingMigration(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          sourceEnvironment = sourceEnvironment.name,
          sourceScenarioVersion = sourceScenarioVersion.value.toString,
        )
      case ScenarioActivity.OutgoingMigration(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersion,
            comment,
            destinationEnvironment
          ) =>
        Dtos.ScenarioActivity.OutgoingMigration(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          comment = toDto(comment),
          destinationEnvironment = destinationEnvironment.name,
        )
      case ScenarioActivity.PerformedSingleExecution(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersion,
            dateFinished,
            errorMessage
          ) =>
        Dtos.ScenarioActivity.PerformedSingleExecution(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          dateFinished = dateFinished,
          errorMessage = errorMessage,
        )
      case ScenarioActivity.PerformedScheduledExecution(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersion,
            dateFinished,
            errorMessage
          ) =>
        Dtos.ScenarioActivity.PerformedScheduledExecution(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          dateFinished = dateFinished,
          errorMessage = errorMessage,
        )
      case ScenarioActivity.AutomaticUpdate(
            _,
            scenarioActivityId,
            user,
            date,
            scenarioVersion,
            dateFinished,
            changes,
            errorMessage
          ) =>
        Dtos.ScenarioActivity.AutomaticUpdate(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          dateFinished = dateFinished,
          changes = changes,
          errorMessage = errorMessage,
        )
      case ScenarioActivity.CustomAction(_, scenarioActivityId, user, date, scenarioVersion, actionName) =>
        Dtos.ScenarioActivity.CustomAction(
          id = scenarioActivityId.value,
          user = user.name.value,
          date = date,
          scenarioVersion = scenarioVersion.map(_.value),
          actionName = actionName,
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

  private def editComment(request: EditCommentRequest)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      dbioActionRunner.run(
        scenarioActivityRepository.editComment(ScenarioActivityId(request.scenarioActivityId), request.commentContent)
      )
    ).leftMap(_ => NoComment(request.scenarioActivityId.toString))

  private def deleteComment(request: DeleteCommentRequest)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    EitherT(
      dbioActionRunner.run(scenarioActivityRepository.deleteComment(ScenarioActivityId(request.scenarioActivityId)))
    ).leftMap(_ => NoComment(request.scenarioActivityId.toString))

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
