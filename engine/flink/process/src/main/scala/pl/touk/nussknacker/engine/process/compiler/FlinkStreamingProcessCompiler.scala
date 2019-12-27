package pl.touk.nussknacker.engine.process.compiler

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar

class FlinkStreamingProcessCompiler(modelData: ModelData, diskStateBackendSupport: Boolean = true)
  extends FlinkProcessCompiler(modelData, diskStateBackendSupport) {

  def createFlinkProcessRegistrar() = FlinkStreamingProcessRegistrar(this)
}
