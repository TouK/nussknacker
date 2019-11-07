package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.management.FlinkBatchRestManager.MainClassName
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class FlinkBatchRestManager(config: FlinkConfig, modelData: ModelData)
                           (implicit backend: SttpBackend[Future, Nothing, NothingT])
  extends FlinkRestManager(config, modelData, mainClassName = MainClassName)

object FlinkBatchRestManager {
  val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkBatchProcessMain"
}