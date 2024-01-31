package pl.touk.nussknacker.ui.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.BusinessError
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
import scala.concurrent.ExecutionContext

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
      .serverSecurityLogic(authorizeKnownUser[BusinessError])
      .serverLogicWithNuExceptionHandling { _: LoggedUser => scenarioName: ProcessName =>
        for {
          scenarioId       <- scenarioService.getProcessId(scenarioName)
          scenarioActivity <- scenarioActivityRepository.findActivity(scenarioId)
        } yield ScenarioActivity(scenarioActivity)
      }
  }

  expose {
    scenarioActivityApiEndpoints.addCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[BusinessError])
      .serverLogicWithNuExceptionHandling { implicit loggedUser => request: AddCommentRequest =>
        for {
          scenarioId <- scenarioAuthorizer.check(request.scenarioName, Permission.Write)
          _ <- scenarioActivityRepository.addComment(scenarioId, request.versionId, UserComment(request.commentContent))
        } yield ()
      }
  }

  expose {
    scenarioActivityApiEndpoints.deleteCommentEndpoint
      .serverSecurityLogic(authorizeKnownUser[BusinessError])
      .serverLogicWithNuExceptionHandling { implicit loggedUser => request: DeleteCommentRequest =>
        for {
          _ <- scenarioAuthorizer.check(request.scenarioName, Permission.Write)
          - <- scenarioActivityRepository.deleteComment(request.commentId)
        } yield ()
      }
  }

  expose {
    scenarioActivityApiEndpoints.addAttachmentEndpoint
      .serverSecurityLogic(authorizeKnownUser[BusinessError])
      .serverLogicWithNuExceptionHandling { implicit loggedUser => request: AddAttachmentRequest =>
        for {
          scenarioId <- scenarioAuthorizer.check(request.scenarioName, Permission.Write)
          _ <- attachmentService
            .saveAttachment(scenarioId, request.versionId, request.fileName.value, request.streamBody)
        } yield ()
      }
  }

  expose {
    scenarioActivityApiEndpoints.downloadAttachmentEndpoint
      .serverSecurityLogic(authorizeKnownUser[BusinessError])
      .serverLogicWithNuExceptionHandling { _: LoggedUser => request: GetAttachmentRequest =>
        for {
          _               <- scenarioService.getProcessId(request.scenarioName)
          maybeAttachment <- attachmentService.readAttachment(request.attachmentId)
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

}
