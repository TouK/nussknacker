package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompiler

object FlinkVerificationMain extends FlinkRunner {

  def run(modelData: ModelData, processJson: String, processVersion: ProcessVersion, savepointPath: String, configuration: Configuration): Unit = {
    val process = readProcessFromArg(processJson)
    new FlinkVerificationMain(modelData, process,processVersion, savepointPath, configuration).runTest()
  }

}


case class FlinkVerificationMain(modelData: ModelData, process: EspProcess, processVersion: ProcessVersion, savepointPath: String,
                                 configuration: Configuration)
  extends FlinkStubbedRunner {
  def runTest(): Unit = {
    val env = createEnv
    val registrar: FlinkStreamingProcessRegistrar = FlinkStreamingProcessRegistrar(new VerificationFlinkProcessCompiler(
      process, env.getConfig, modelData.configCreator, modelData.processConfigFromConfiguration), modelData.processConfig)
    registrar.register(env, process, processVersion, Option(TestRunId("dummy")))
    execute(env, SavepointRestoreSettings.forPath(savepointPath, true))
  }
}