package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.management.sample.modelconfig.SampleModelConfigLoader
import pl.touk.nussknacker.engine.process
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData

class SampleComponentProviderTest extends FunSuite with FlinkSpec with Matchers {

  override protected lazy val config = ConfigFactory.empty()

  private val configCreator: DevProcessConfigCreator = new DevProcessConfigCreator

  test("detects component service") {
    val process =
      EspProcessBuilder
        .id("sample_notification")
        .parallelism(1)
        .source("start", "boundedSource", "elements" -> "{'one'}")
        .processor("service1", "providedComponent-component-v1", "fromConfig-v1" -> "''")
        .processor("service2", "providedComponent-component-v2", "fromConfig-v2" -> "''")
        .emptySink("end", "monitor")


    run(process) {
      //should not fail
    }
  }


  private var registrar: FlinkProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val loadedConfig = new SampleModelConfigLoader().resolveInputConfigDuringExecution(config, getClass.getClassLoader)
    val modelData = LocalModelData(loadedConfig.config, configCreator)
    registrar = process.registrar.FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
  }

  private def run(process: EspProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

}
