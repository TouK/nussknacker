package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar

object FlinkVerificationMain extends FlinkRunner {

  def run(modelData: ModelData, processJson: String, processVersion: ProcessVersion, savepointPath: String, configuration: Configuration): Unit = {
    val process = readProcessFromArg(processJson)
    new FlinkVerificationMain(modelData, process,processVersion, savepointPath, configuration).runTest()
  }

}


class FlinkVerificationMain(val modelData: ModelData, val process: EspProcess, processVersion: ProcessVersion, savepointPath: String,
                            val configuration: Configuration) extends FlinkStubbedRunner {

  def runTest(): Unit = {
    val env = createEnv
    val registrar = prepareRegistrar()
    registrar.register(env, process, processVersion, Option(TestRunId("dummy")))
    execute(env, SavepointRestoreSettings.forPath(savepointPath, true))
  }

  protected def prepareRegistrar(): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(new VerificationFlinkProcessCompiler(
      process, modelData.configCreator, InputConfigDuringExecution(modelData.inputConfig), modelData.modelConfigLoader, modelData.objectNaming),
      modelData.processConfig, ExecutionConfigPreparer.defaultChain(modelData))
  }
}
