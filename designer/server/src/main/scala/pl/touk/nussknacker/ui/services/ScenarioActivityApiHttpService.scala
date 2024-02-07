package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.ScenarioActivityApiEndpoints.Dtos.ScenarioActivityError.{
  NoComment,
  NoPermission,
  NoScenario
}
import pl.touk.nussknacker.ui.api.ScenarioActivityApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.api.{AuthorizeProcess, ScenarioActivityApiEndpoints}
import pl.touk.nussknacker.ui.process.{ProcessService, ScenarioAttachmentService}
import pl.touk.nussknacker.ui.process.repository.{ProcessActivityRepository, UserComment}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.server.TapirStreamEndpointProvider
import pl.touk.nussknacker.ui.server.HeadersSupport.ContentDisposition
import pl.touk.nussknacker.ui.util.EitherTImplicits
import sttp.model.MediaType

import java.io.ByteArrayInputStream
import java.net.URLConnection
import scala.concurrent.{ExecutionContext, Future}

class ScenarioActivityApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    scenarioActivityRepository: ProcessActivityRepository,
    scenarioService: ProcessService,
    scenarioAuthorizer: AuthorizeProcess,
    attachmentService: ScenarioAttachmentService,
    streamEndpointProvider: TapirStreamEndpointProvider
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {
  import EitherTImplicits._

  private val scenarioActivityApiEndpoints = new ScenarioActivityApiEndpoints(authenticator.authenticationMethod())

  expose {
    scenarioActivityApiEndpoints.scenarioActivityEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { _: LoggedUser => scenarioName: ProcessName =>
        for {
          scenarioId       <- getScenarioIdByName(scenarioName)
          scenarioActivity <- scenarioActivityRepository.findActivity(scenarioId).eitherT()
        } yield ScenarioActivity(scenarioActivity)
      }
  }

  expose {
    scenarioActivityApiEndpoints.addCommentEndpoint
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
    scenarioActivityApiEndpoints.deleteCommentEndpoint
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
    scenarioActivityApiEndpoints
      .addAttachmentEndpoint(streamEndpointProvider.streamBodyEndpointInput)
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
    scenarioActivityApiEndpoints
      .downloadAttachmentEndpoint(streamEndpointProvider.streamBodyEndpointOutput)
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityError])
      .serverLogicEitherT { _: LoggedUser => request: GetAttachmentRequest =>
        for {
          _               <- getScenarioIdByName(request.scenarioName)
          maybeAttachment <- attachmentService.readAttachment(request.attachmentId).eitherT()
          response = buildResponse(maybeAttachment)
        } yield response
      }
  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    scenarioService
      .getProcessId(scenarioName)
      .toRightEitherT(NoScenario(scenarioName))
  }

  private def isAuthorized(scenarioId: ProcessId, permission: Permission)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    scenarioAuthorizer
      .check(scenarioId, permission, loggedUser)
      .map[Either[ScenarioActivityError, Unit]] {
        case true  => Right(())
        case false => Left(NoPermission)
      }
      .eitherT()

  private def addNewComment(request: AddCommentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    scenarioActivityRepository
      .addComment(scenarioId, request.versionId, UserComment(request.commentContent))
      .eitherT[ScenarioActivityError]()

  private def deleteComment(request: DeleteCommentRequest): EitherT[Future, ScenarioActivityError, Unit] =
    scenarioActivityRepository
      .deleteComment(request.commentId)
      .eitherTMapE[ScenarioActivityError](_ => NoComment(request.commentId))

  private def saveAttachment(request: AddAttachmentRequest, scenarioId: ProcessId)(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, ScenarioActivityError, Unit] =
    attachmentService
      .saveAttachment(scenarioId, request.versionId, request.fileName.value, request.body)
      .eitherT()

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
