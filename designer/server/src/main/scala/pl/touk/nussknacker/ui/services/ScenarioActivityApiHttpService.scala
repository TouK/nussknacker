package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName}
import pl.touk.nussknacker.restmodel.SecurityError.AuthorizationError
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.api.ScenarioActivityApiEndpoints.Dtos._
import pl.touk.nussknacker.ui.api.{AkkaToTapirStreamExtension, AuthorizeProcess, ScenarioActivityApiEndpoints}
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.repository.{ProcessActivityRepository, UserComment}
import pl.touk.nussknacker.ui.security.api.{AuthenticationResources, LoggedUser}
import pl.touk.nussknacker.ui.server.HeadersSupport.ContentDisposition
import sttp.model.MediaType
import sttp.tapir.EndpointIO.StreamBodyWrapper

import java.io.{ByteArrayInputStream, InputStream}
import java.net.URLConnection
import scala.concurrent.{ExecutionContext, Future}

class ScenarioActivityApiHttpService(
    config: Config,
    authenticator: AuthenticationResources,
    scenarioActivityRepository: ProcessActivityRepository,
    scenarioService: ProcessService,
    scenarioAuthorizer: AuthorizeProcess,
    attachmentService: ScenarioAttachmentService,
    akkaToTapirStreamExtension: AkkaToTapirStreamExtension
)(implicit executionContext: ExecutionContext)
    extends BaseHttpService(config, authenticator)
    with LazyLogging {

  private val scenarioActivityApiEndpoints = new ScenarioActivityApiEndpoints(authenticator.authenticationMethod())
  private implicit val streamBodyWrapper: StreamBodyWrapper[_, InputStream] =
    akkaToTapirStreamExtension.streamBodyEndpointInOut

  expose {
    scenarioActivityApiEndpoints.scenarioActivityEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { _: LoggedUser => scenarioName: ProcessName =>
        for {
          scenarioId       <- scenarioService.getProcessId(scenarioName)
          scenarioActivity <- scenarioActivityRepository.findActivity(scenarioId)
        } yield success(ScenarioActivity(scenarioActivity))
      }
  }

  expose {
    scenarioActivityApiEndpoints.addCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => request: AddCommentRequest =>
        checkWrite(request.scenarioName) { scenarioId =>
          scenarioActivityRepository
            .addComment(scenarioId, request.versionId, UserComment(request.commentContent))
            .map(success)
        }
      }
  }

  expose {
    scenarioActivityApiEndpoints.deleteCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => request: DeleteCommentRequest =>
        checkWrite(request.scenarioName) { _ =>
          scenarioActivityRepository
            .deleteComment(request.commentId)
            .map(success)
        }
      }
  }

  expose {
    scenarioActivityApiEndpoints.addAttachmentEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { implicit loggedUser => request: AddAttachmentRequest =>
        checkWrite(request.scenarioName) { scenarioId: ProcessId =>
          attachmentService
            .saveAttachment(scenarioId, request.versionId, request.fileName.value, request.streamBody)
            .map(success)
        }
      }
  }

  expose {
    scenarioActivityApiEndpoints.downloadAttachmentEndpoint
      .serverSecurityLogic(authorizeKnownUser[String])
      .serverLogic { _: LoggedUser => request: GetAttachmentRequest =>
        (
          for {
            _               <- scenarioService.getProcessId(request.scenarioName)
            maybeAttachment <- attachmentService.readAttachment(request.attachmentId)
          } yield maybeAttachment
        )
          .map(maybeAttachment =>
            maybeAttachment
              .fold(GetAttachmentResponse.emptyResponse) { case (fileName, content) =>
                GetAttachmentResponse(
                  new ByteArrayInputStream(content),
                  ContentDisposition.fromFileNameString(fileName).headerValue(),
                  Option(URLConnection.guessContentTypeFromName(fileName))
                    .getOrElse(MediaType.ApplicationOctetStream.toString())
                )
              }
          )
          .map(success)
      }
  }

  private def checkWrite[E, R](scenarioName: ProcessName)(
      businessLogic: ProcessId => Future[LogicResult[E, R]]
  )(implicit loggedUser: LoggedUser): Future[LogicResult[E, R]] = {
    for {
      scenarioId <- scenarioService.getProcessId(scenarioName)
      canWrite   <- scenarioAuthorizer.check(scenarioId, Permission.Write, loggedUser)
      result     <- if (canWrite) businessLogic(scenarioId) else Future.successful(securityError(AuthorizationError))
    } yield result
  }

}
