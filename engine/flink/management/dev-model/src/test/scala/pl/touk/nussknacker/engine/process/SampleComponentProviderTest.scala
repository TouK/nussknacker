package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.{ModelData, process}

class SampleComponentProviderTest extends AnyFunSuite with FlinkSpec with Matchers {

  override protected lazy val config = ConfigFactory.empty()

  test("detects component service") {
    val process =
      ScenarioBuilder
        .streaming("sample_notification")
        .parallelism(1)
        .source("start", "boundedSource", "elements" -> "{'one'}")
        .processor("service1", "providedComponent-component-v1", "fromConfig-v1" -> "''")
        .processor("service2", "providedComponent-component-v2", "fromConfig-v2" -> "''")
        .emptySink("end", "monitor")

    run(process) {
      // should not fail
    }
  }

  private var registrar: FlinkProcessRegistrar = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val modelData = ModelData(config, ModelClassLoader(List.empty), None)
    registrar = process.registrar.FlinkProcessRegistrar(
      new FlinkProcessCompilerDataFactory(modelData),
      ExecutionConfigPreparer.unOptimizedChain(modelData)
    )
  }

  private def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    registrar.register(env, process, ProcessVersion.empty, DeploymentData.empty)
    env.withJobRunning(process.id)(action)
  }

}
