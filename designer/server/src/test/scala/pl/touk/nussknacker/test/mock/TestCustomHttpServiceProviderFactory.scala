package pl.touk.nussknacker.test.mock

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import derevo.circe.{decoder, encoder}
import derevo.derive
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProvider,
  CustomHttpServiceProviderFactory,
  PekkoCustomHttpServiceProvider,
  TapirCustomHttpServiceProvider
}
import pl.touk.nussknacker.ui.customhttpservice.TapirCustomHttpServiceProvider.CustomHttpServiceServerEndpointDefinition
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.security.api.LoggedUser
import sttp.model.StatusCode.UnprocessableEntity
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody

class TestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def name: String = "testProvider"

  override def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService
  ): Resource[IO, CustomHttpServiceProvider] =
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
  ): Resource[IO, CustomHttpServiceProvider] =
    Resource.pure(TestCustomHttpServiceProvider)

}

class TestTapirCustomHttpServiceProvider extends TapirCustomHttpServiceProvider {

  import sttp.tapir._

  import TestTapirCustomHttpServiceProvider._

  private val tag = "Tapir based custom HTTP service"

  private lazy val serverEndpointDefinition =
    new CustomHttpServiceServerEndpointDefinition {

      type REQUEST  = SecuredRequest
      type ERROR    = SampleError
      type RESPONSE = SecuredResponse

      override def definition: Endpoint[Unit, SecuredRequest, SampleError, SecuredResponse, Any] =
        endpoint
          .tag(tag)
          .description("A sample endpoint")
          .post
          .in("sample")
          .in(jsonBody[SecuredRequest])
          .out(jsonBody[SecuredResponse])
          .errorOut(
            statusCode(UnprocessableEntity)
              .and(plainBody[SampleError])
          )

      override def logic(user: LoggedUser, request: SecuredRequest): IO[Either[SampleError, SecuredResponse]] = {
        if (request.returnInternalError) {
          IO.raiseError(new RuntimeException("Internal error"))
        } else {
          IO.delay(
            if (request.returnBusinessError) {
              Left(SampleError("Sample error"))
            } else {
              Right(
                SecuredResponse(
                  message = s"You send message: '${request.message}''",
                  username = user.username
                )
              )
            }
          )
        }
      }
    }

  override def serverEndpointDefinitions: List[CustomHttpServiceServerEndpointDefinition] = List(
    serverEndpointDefinition,
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
  ): Resource[IO, CustomHttpServiceProvider] =
    Resource.pure(new TestTapirCustomHttpServiceProvider)

}
