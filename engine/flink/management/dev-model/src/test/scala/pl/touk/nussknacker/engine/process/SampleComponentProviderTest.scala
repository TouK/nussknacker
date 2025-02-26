package pl.touk.nussknacker.engine.process

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.{ClassLoaderModelData, ConfigWithUnresolvedVersion}
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.test.ScalatestMiniClusterJobStatusCheckingOps.miniClusterWithServicesToOps
import pl.touk.nussknacker.engine.process.runner.FlinkScenarioUnitTestJob
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

class SampleComponentProviderTest extends AnyFunSuite with FlinkSpec with Matchers {

  override protected lazy val config: Config = ConfigFactory.empty()

  test("detects component service") {
    val process =
      ScenarioBuilder
        .streaming("sample_notification")
        .parallelism(1)
        .source("start", "boundedSource", "elements" -> "{'one'}".spel)
        .processor("service1", "providedComponent-component-v1", "fromConfig-v1" -> "''".spel)
        .processor("service2", "providedComponent-component-v2", "fromConfig-v2" -> "''".spel)
        .emptySink("end", "monitor")

    run(process) {
      // should not fail
    }
  }

  private val modelData =
    ClassLoaderModelData(
      _.resolveInputConfigDuringExecution(ConfigWithUnresolvedVersion(ConfigFactory.empty), getClass.getClassLoader),
      ModelClassLoader.empty,
      category = None,
      componentId => DesignerWideComponentId(componentId.toString),
      additionalConfigsFromProvider = Map.empty,
      ComponentDefinitionExtractionMode.FinalDefinition
    )

  private def run(process: CanonicalProcess)(action: => Unit): Unit = {
    flinkMiniCluster.withDetachedStreamExecutionEnvironment { env =>
      val executionResult = new FlinkScenarioUnitTestJob(modelData).run(process, env)
      flinkMiniCluster.withRunningJob(executionResult.getJobID)(action)
    }
  }

}
