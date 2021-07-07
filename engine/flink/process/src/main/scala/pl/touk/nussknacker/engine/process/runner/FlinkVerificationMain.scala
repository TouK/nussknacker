package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testmode.TestRunId

object FlinkVerificationMain extends FlinkRunner {

  def run(modelData: ModelData, processJson: String, processVersion: ProcessVersion, deploymentData: DeploymentData, savepointPath: String, configuration: Configuration): Unit = {
    val process = readProcessFromArg(processJson)
    new FlinkVerificationMain(modelData, process,processVersion, deploymentData, savepointPath, configuration).runTest()
  }

}


class FlinkVerificationMain(val modelData: ModelData, val process: EspProcess, processVersion: ProcessVersion, deploymentData: DeploymentData, savepointPath: String,
                            val configuration: Configuration) extends FlinkStubbedRunner {

  def runTest(): Unit = {
    val env = createEnv
    val registrar = prepareRegistrar()
    registrar.register(env, process, processVersion, deploymentData, Option(TestRunId("dummy")))
    execute(env, SavepointRestoreSettings.forPath(savepointPath, true))
  }

  protected def prepareRegistrar(): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(new VerificationFlinkProcessCompiler(
      process, modelData.configCreator, modelData.processConfig, modelData.objectNaming), ExecutionConfigPreparer.defaultChain(modelData), RunMode.Verification)
  }
}
