package pl.touk.nussknacker.ui.customhttpservice

import cats.effect.{Async, Resource}
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService

import scala.language.higherKinds

trait CustomHttpServiceProviderFactory {

  def name: String

  def create[M[_]: Async](
      config: Config,
      services: NussknackerServicesForCustomHttpService[M],
  ): Resource[M, CustomHttpServiceProvider]

}
