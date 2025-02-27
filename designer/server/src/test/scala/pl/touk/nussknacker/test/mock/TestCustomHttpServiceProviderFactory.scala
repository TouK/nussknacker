package pl.touk.nussknacker.test.mock

import akka.http.scaladsl.server.{Directives, Route}
import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.customhttpservice.{CustomHttpServiceProvider, CustomHttpServiceProviderFactory}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class TestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService
  )(implicit executionContext: ExecutionContext): Resource[IO, CustomHttpServiceProvider] =
    Resource.pure(TestCustomHttpServiceProvider)

}

object TestCustomHttpServiceProvider extends CustomHttpServiceProvider with Directives {

  override def provideRouteWithUser(implicit user: LoggedUser): Route =
    path("testPathPart1" / "testPathPart2") {
      get { complete("testResponse") }
    }

  override def provideRouteWithoutUser(): Route =
    path("public" / "testPathPart1" / "testPathPart2") {
      get { complete("testResponse") }
    }

}
