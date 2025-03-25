package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.{IO, Resource}
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService

trait CustomHttpServiceProviderFactory {

  def name: String

  def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService,
  )(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime): Resource[IO, CustomHttpServiceProvider]

}
