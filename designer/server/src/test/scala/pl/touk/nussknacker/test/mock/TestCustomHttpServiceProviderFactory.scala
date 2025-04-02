package pl.touk.nussknacker.test.mock

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
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
import sttp.tapir.{endpoint, stringBody}
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

  private lazy val publicEndpoint =
    endpoint.get
      .in("public")
      .out(stringBody)

  private lazy val publicServerEndpoint =
    publicEndpoint.serverLogicSuccess(_ => Future.successful("Hello!"))

  override def serverEndpoints: List[ServerEndpoint[PekkoStreams with WebSockets, Future]] = List(
    publicServerEndpoint,
  )

}

class TapirTestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def name: String = "tapirTestProvider"

  override def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime): Resource[IO, CustomHttpServiceProvider] =
    Resource.pure(new TestTapirCustomHttpServiceProvider(services.tapirAuthService))

}
