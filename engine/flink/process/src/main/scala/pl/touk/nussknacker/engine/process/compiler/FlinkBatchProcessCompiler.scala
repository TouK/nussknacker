package pl.touk.nussknacker.engine.process.compiler

import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.process.FlinkBatchProcessRegistrar

class FlinkBatchProcessCompiler(modelData: ModelData)
  extends FlinkProcessCompiler(modelData, diskStateBackendSupport = true) {

  def createFlinkProcessRegistrar(): FlinkBatchProcessRegistrar = FlinkBatchProcessRegistrar(this, config)
}
