package pl.touk.nussknacker.test.mock

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import derevo.circe.{decoder, encoder}
import derevo.derive
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProvider,
  CustomHttpServiceProviderFactory,
  PekkoCustomHttpServiceProvider,
  TapirCustomHttpServiceProvider
}
import pl.touk.nussknacker.ui.customhttpservice.services.{NussknackerServicesForCustomHttpService, TapirEndpointSupport}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.capabilities.WebSockets
import sttp.capabilities.pekko.PekkoStreams
import sttp.model.StatusCode.UnprocessableEntity
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.server.ServerEndpoint

import scala.concurrent.Future

class TestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def name: String = "testProvider"

  override def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime): Resource[IO, CustomHttpServiceProvider] =
    Resource.pure(TestCustomHttpServiceProvider)

}

object TestCustomHttpServiceProvider extends PekkoCustomHttpServiceProvider with Directives {

  override def provideRouteWithUser(implicit user: LoggedUser): Route =
    path("testPathPart") {
      get { complete("testResponse") }
    }

}

class SecondTestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def name: String = "secondTestProvider"

  override def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime): Resource[IO, CustomHttpServiceProvider] =
    Resource.pure(TestCustomHttpServiceProvider)

}

class TestTapirCustomHttpServiceProvider(tapirEndpointSupport: TapirEndpointSupport)
    extends TapirCustomHttpServiceProvider {

  import sttp.tapir._
  import tapirEndpointSupport._

  import TestTapirCustomHttpServiceProvider._

  private val tag = "Tapir based custom HTTP service"

  private lazy val publicEndpoint =
    endpoint
      .tag(tag)
      .description("Public endpoint")
      .get
      .in("public")
      .out(stringBody)

  private lazy val publicServerEndpoint =
    publicEndpoint.serverLogicSuccess(_ => Future.successful("Hello from public endpoint!"))

  private lazy val securedEndpoint =
    endpoint
      .tag(tag)
      .description("Secured endpoint")
      .post
      .in("secured")
      .in(jsonBody[SecuredRequest])
      .out(jsonBody[SecuredResponse])
      .errorOut(
        statusCode(UnprocessableEntity)
          .and(plainBody[SampleError])
      )
      .secure

  private lazy val securedServerEndpoint =
    securedEndpoint
      .serverSecurityLogic(authorizeKnownUser[SampleError])
      .serverLogic { loggedUser => request =>
        if (request.returnInternalError) {
          Future.failed(new RuntimeException("Internal error"))
        } else {
          Future.successful(
            if (request.returnBusinessError) {
              businessError(SampleError("Sample error"))
            } else {
              success(
                SecuredResponse(
                  message = s"You send message: '${request.message}''",
                  username = loggedUser.username
                )
              )
            }
          )
        }
      }

  override def serverEndpoints: List[ServerEndpoint[PekkoStreams with WebSockets, Future]] = List(
    publicServerEndpoint,
    securedServerEndpoint,
  )

}

object TestTapirCustomHttpServiceProvider {

  import sttp.tapir._

  @derive(encoder, decoder, schema)
  final case class SecuredRequest(
      message: String,
      returnBusinessError: Boolean = false,
      returnInternalError: Boolean = false
  )

  @derive(encoder, decoder, schema)
  final case class SecuredResponse(message: String, username: String)

  final case class SampleError(cause: String)

  implicit val sampleErrorCodec: Codec[String, SampleError, CodecFormat.TextPlain] = Codec.string
    .mapDecode(_ =>
      DecodeResult.Error(
        "Error should be never decoded",
        new RuntimeException("Error should be never decoded")
      )
    )(_.cause)

}

class TapirTestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def name: String = "tapirTestProvider"

  override def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime): Resource[IO, CustomHttpServiceProvider] =
    Resource.pure(new TestTapirCustomHttpServiceProvider(services.tapirAuthService))

}
