package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.process.FlinkBatchProcessRegistrar

class FlinkBatchProcessCompiler(creator: ProcessConfigCreator, config: Config)
  extends FlinkProcessCompiler(creator, config, diskStateBackendSupport = true) {

  def createFlinkProcessRegistrar(): FlinkBatchProcessRegistrar = FlinkBatchProcessRegistrar(this, config)
}
