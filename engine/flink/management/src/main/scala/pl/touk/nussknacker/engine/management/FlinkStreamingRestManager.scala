package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.management.FlinkStreamingRestManager.MainClassName
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

class FlinkStreamingRestManager(config: FlinkConfig, modelData: BaseModelData)
                               (implicit ec: ExecutionContext, backend: SttpBackend[Future, Any])
  extends FlinkRestManager(config, modelData, mainClassName = MainClassName)

object FlinkStreamingRestManager {
  val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain"
}
