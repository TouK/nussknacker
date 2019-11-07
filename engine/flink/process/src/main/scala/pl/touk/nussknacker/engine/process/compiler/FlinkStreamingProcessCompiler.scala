package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar

class FlinkStreamingProcessCompiler(creator: ProcessConfigCreator, config: Config, diskStateBackendSupport: Boolean = true)
  extends FlinkProcessCompiler(creator, config, diskStateBackendSupport) {

  def createFlinkProcessRegistrar() = FlinkStreamingProcessRegistrar(this, config)
}
