package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.{IO, Resource}
import cats.effect.unsafe.IORuntime
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService

import scala.concurrent.ExecutionContext

trait CustomHttpServiceProviderFactory {

  def name: String

  def create(
      config: Config,
      services: NussknackerServicesForCustomHttpService,
  )(implicit executionContext: ExecutionContext, ioRuntime: IORuntime): Resource[IO, CustomHttpServiceProvider]

}
