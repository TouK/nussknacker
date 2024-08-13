package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.DesignerWideComponentId
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import pl.touk.nussknacker.engine.process.runner.UnitTestsFlinkRunner
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.engine.{ClassLoaderModelData, ConfigWithUnresolvedVersion}

class SampleComponentProviderTest extends AnyFunSuite with FlinkSpec with Matchers {

  override protected lazy val config = ConfigFactory.empty()

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
      // This ugly hack is because of Idea classloader issue, see comment in ClassLoaderModelData
      shouldIncludeConfigCreator = {
        case _: DevProcessConfigCreator => true
        case _                          => false
      },
      shouldIncludeComponentProvider = _ => true
    )

  private def run(process: CanonicalProcess)(action: => Unit): Unit = {
    val env = flinkMiniCluster.createExecutionEnvironment()
    UnitTestsFlinkRunner.registerInEnvironmentWithModel(env, modelData)(process)
    env.withJobRunning(process.name.value)(action)
  }

}
