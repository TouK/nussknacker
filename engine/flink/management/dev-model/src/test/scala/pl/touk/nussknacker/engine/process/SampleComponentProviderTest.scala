package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.Implicits._

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

  private val modelData = ModelData.duringFlinkExecution(config)

  private def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(env, modelData)(process)
    env.withJobRunning(process.name.value)(action)
  }

}
