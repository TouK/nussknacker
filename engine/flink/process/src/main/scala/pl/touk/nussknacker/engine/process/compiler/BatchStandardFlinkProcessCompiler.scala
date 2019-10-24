package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.process.{BatchFlinkProcessRegistrar, FlinkProcessRegistrar}

class BatchStandardFlinkProcessCompiler(creator: ProcessConfigCreator, config: Config)
  extends FlinkProcessCompiler(creator, config, diskStateBackendSupport = true) {

  def createBatchFlinkProcessRegistrar(): BatchFlinkProcessRegistrar = {
    BatchFlinkProcessRegistrar(this, config)
  }
}
