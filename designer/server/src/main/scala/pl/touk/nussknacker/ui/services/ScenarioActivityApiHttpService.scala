package pl.touk.nussknacker.ui.services

import cats.data.EitherT
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.api.ScenarioActivityApiEndpoints.Dtos.ScenarioActivityErrors.{
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
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityErrors])
      .serverLogicEitherT { _: LoggedUser => scenarioName: ProcessName =>
        for {
          scenarioId <- scenarioService
            .getProcessId(scenarioName)
            .toRightEitherT(NoScenario(scenarioName))
          scenarioActivity <- EitherT.liftF(scenarioActivityRepository.findActivity(scenarioId))
        } yield ScenarioActivity(scenarioActivity)
      }
  }

  expose {
    scenarioActivityApiEndpoints.addCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityErrors])
      .serverLogicEitherT { implicit loggedUser => request: AddCommentRequest =>
        for {
          scenarioId <- scenarioService
            .getProcessId(request.scenarioName)
            .toRightEitherT(NoScenario(request.scenarioName))
          _ <- isAuthorized(scenarioId, Permission.Write).eitherT()
          _ <- scenarioActivityRepository
            .addComment(scenarioId, request.versionId, UserComment(request.commentContent))
            .eitherT()
        } yield ()
      }
  }

  expose {
    scenarioActivityApiEndpoints.deleteCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityErrors])
      .serverLogicEitherT { implicit loggedUser => request: DeleteCommentRequest =>
        for {
          scenarioId <- scenarioService
            .getProcessId(request.scenarioName)
            .toRightEitherT(NoScenario(request.scenarioName))
          _ <- isAuthorized(scenarioId, Permission.Write).eitherT()
          _ <- scenarioActivityRepository
            .deleteComment(request.commentId)
            .eitherTMapE[ScenarioActivityErrors](_ => NoComment(request.commentId))
        } yield ()
      }
  }

  expose {
    scenarioActivityApiEndpoints
      .addAttachmentEndpoint(streamEndpointProvider.streamBodyEndpointInOut)
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityErrors])
      .serverLogicEitherT { implicit loggedUser => request: AddAttachmentRequest =>
        for {
          scenarioId <- scenarioService
            .getProcessId(request.scenarioName)
            .toRightEitherT(NoScenario(request.scenarioName))
          _ <- isAuthorized(scenarioId, Permission.Write).eitherT()
          _ <- attachmentService
            .saveAttachment(scenarioId, request.versionId, request.fileName.value, request.body)
            .eitherT()
        } yield ()
      }
  }

  expose {
    scenarioActivityApiEndpoints
      .downloadAttachmentEndpoint(streamEndpointProvider.streamBodyEndpointInOut)
      .serverSecurityLogic(authorizeKnownUser[ScenarioActivityErrors])
      .serverLogicEitherT { _: LoggedUser => request: GetAttachmentRequest =>
        for {
          _ <- scenarioService
            .getProcessId(request.scenarioName)
            .toRightEitherT(NoScenario(request.scenarioName))
          maybeAttachment <- attachmentService.readAttachment(request.attachmentId).eitherT()
          response = maybeAttachment match {
            case Some((fileName, content)) =>
              GetAttachmentResponse(
                inputStream = new ByteArrayInputStream(content),
                fileName = ContentDisposition.fromFileNameString(fileName).headerValue(),
                contentType = Option(URLConnection.guessContentTypeFromName(fileName))
                  .getOrElse(MediaType.ApplicationOctetStream.toString())
              )
            case None => GetAttachmentResponse.emptyResponse
          }
        } yield response
      }
  }

  private def isAuthorized(scenarioId: ProcessId, permission: Permission)(
      implicit loggedUser: LoggedUser
  ): Future[Either[ScenarioActivityErrors, Unit]] =
    scenarioAuthorizer.check(scenarioId, permission, loggedUser).map {
      case true  => Right(())
      case false => Left(NoPermission)
    }

}
