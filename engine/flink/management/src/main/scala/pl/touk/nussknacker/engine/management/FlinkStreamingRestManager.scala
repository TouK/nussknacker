package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.management.FlinkStreamingRestManager.MainClassName
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class FlinkStreamingRestManager(config: FlinkConfig, modelData: ModelData)
                               (implicit backend: SttpBackend[Future, Nothing, NothingT])
  extends FlinkRestManager(config, modelData, mainClassName = MainClassName)

object FlinkStreamingRestManager {
  val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain"
}
