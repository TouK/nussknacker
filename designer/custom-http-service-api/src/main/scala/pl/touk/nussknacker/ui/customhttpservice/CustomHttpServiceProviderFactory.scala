package pl.touk.nussknacker.ui.customhttpservice

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.customhttpservice.services.NussknackerServicesForCustomHttpService

import scala.concurrent.ExecutionContext

trait CustomHttpServiceProviderFactory {

  def create(
      config: Config,
      executionContext: ExecutionContext,
      services: NussknackerServicesForCustomHttpService
  ): CustomHttpServiceProvider

}
