package pl.touk.nussknacker.test.mock

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import org.apache.pekko.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.ui.customhttpservice.{
  CustomHttpServiceProvider,
  CustomHttpServiceProviderFactory,
  PekkoCustomHttpServiceProvider
}
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.security.api.LoggedUser

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
