package pl.touk.nussknacker.engine.management.sample.service

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}

import scala.concurrent.Future

// Used to test reloading model config.
class ModelConfigReaderService(config: Config) extends Service {

  @MethodToInvoke
  def invoke(@ParamName("configPath") configPath: String): Future[String] = {
    Future.successful(config.getString(configPath))
  }

}
