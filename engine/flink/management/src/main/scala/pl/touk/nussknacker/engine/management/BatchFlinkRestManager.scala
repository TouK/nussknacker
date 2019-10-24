package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.ModelData
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

class BatchFlinkRestManager(config: FlinkConfig, modelData: ModelData)
                           (implicit backend: SttpBackend[Future, Nothing, NothingT])
  extends FlinkRestManager(config, modelData, mainClassName = "pl.touk.nussknacker.engine.process.runner.BatchFlinkProcessMain")
