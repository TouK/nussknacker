package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.api.app.AppApiEndpoints
import pl.touk.nussknacker.ui.security.api.{AuthCredentials, AuthenticatedUser}
import sttp.tapir._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.swagger.bundle.SwaggerInterpreter

import scala.concurrent.Future

object NuApiSwagger {

  // todo:
  private implicit val loggedUserCodec: Codec[String, AuthCredentials, CodecFormat.TextPlain] =
    Codec
      .id(CodecFormat.TextPlain(), Schema.string[String])
      .map(
        Mapping
          .from[String, AuthCredentials](
            str => AuthCredentials(str)
          )(
            loggedUser => loggedUser.value
          )
      )

  val publicServerEndpoints: List[ServerEndpoint[Any, Future]] =
    SwaggerInterpreter()
    .fromEndpoints(new AppApiEndpoints(sttp.tapir.auth.basic[AuthCredentials]()).allEndpoints, "Nussknacker Designer API", "1.0.0")
}
