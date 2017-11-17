package pl.touk.nussknacker.engine.process.runner

import java.net.URL

import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompiler

object FlinkVerificationMain extends FlinkRunner {

  def run(modelData: ModelData, processJson: String, savepointPath: String): Unit = {
    val process = readProcessFromArg(processJson)
    new FlinkVerificationMain(modelData, process, savepointPath).runTest()
  }

}


case class FlinkVerificationMain(modelData: ModelData,
                                 process: EspProcess, savepointPath: String)
  extends FlinkStubbedRunner {
  
  def runTest(): Unit = {
    val env = createEnv
    val registrar: FlinkProcessRegistrar = new VerificationFlinkProcessCompiler(
      process, env.getConfig, modelData.configCreator,
      modelData.processConfig).createFlinkProcessRegistrar()
    registrar.register(env, process, Option(TestRunId("dummy")))
    execute(env, SavepointRestoreSettings.forPath(savepointPath, true))
  }
}