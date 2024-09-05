package pl.touk.nussknacker.ui.api

import cats.data.EitherT
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.deployment.ScenarioActivityId
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos.ScenarioActivityError.{
  NoComment,
  NoPermission,
  NoScenario
}
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Dtos._
import pl.touk.nussknacker.ui.api.description.scenarioActivity.Endpoints
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
import scala.util.Random

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
