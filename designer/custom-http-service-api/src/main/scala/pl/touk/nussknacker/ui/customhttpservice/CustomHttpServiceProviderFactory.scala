package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService

import scala.concurrent.ExecutionContext

trait CustomHttpServiceProviderFactory {

  def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService
  )(implicit executionContext: ExecutionContext): Resource[IO, CustomHttpServiceProvider]

}
