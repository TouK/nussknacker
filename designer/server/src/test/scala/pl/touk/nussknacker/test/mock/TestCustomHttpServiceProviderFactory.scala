package pl.touk.nussknacker.test.mock

import akka.http.scaladsl.server.{Directives, Route}
import cats.effect.{Async, Resource}
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.customhttpservice.{CustomHttpServiceProvider, CustomHttpServiceProviderFactory}
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.language.higherKinds

class TestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def name: String = "testProvider"

  override def create[M[_]: Async](
      config: Config,
      services: NussknackerServicesForCustomHttpService[M]
  ): Resource[M, CustomHttpServiceProvider] =
    Resource.pure(TestCustomHttpServiceProvider)

}

object TestCustomHttpServiceProvider extends CustomHttpServiceProvider with Directives {

  override def provideRouteWithUser(implicit user: LoggedUser): Route =
    path("testPathPart") {
      get { complete("testResponse") }
    }

}

class SecondTestCustomHttpServiceProviderFactory extends CustomHttpServiceProviderFactory {

  override def name: String = "secondTestProvider"

  override def create[M[_]: Async](
      config: Config,
      services: NussknackerServicesForCustomHttpService[M]
  ): Resource[M, CustomHttpServiceProvider] =
    Resource.pure(TestCustomHttpServiceProvider)

}
