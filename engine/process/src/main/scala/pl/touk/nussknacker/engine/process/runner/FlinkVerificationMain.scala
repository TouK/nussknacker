package pl.touk.nussknacker.engine.process.runner

import java.net.URL

import com.typesafe.config.Config
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompiler

object FlinkVerificationMain extends FlinkRunner {

  def run(processJson: String, config: Config, savepointPath: String, urls: List[URL]): Unit = {
    val process = readProcessFromArg(processJson)
    val creator: ProcessConfigCreator = loadCreator(config)

    new FlinkVerificationMain(config, urls, process, creator, savepointPath).runTest()
  }

}


class FlinkVerificationMain(config: Config, val urls: List[URL],
                            val process: EspProcess, creator: ProcessConfigCreator, savepointPath: String)
  extends Serializable with FlinkStubbedRunner {

  def runTest(): Unit = {
    val env = createEnv
    val registrar: FlinkProcessRegistrar = new VerificationFlinkProcessCompiler(
      process, env.getConfig, creator,
      config).createFlinkProcessRegistrar()
    registrar.register(env, process, Option(TestRunId("dummy")))
    execute(env, SavepointRestoreSettings.forPath(savepointPath, true))
  }
}