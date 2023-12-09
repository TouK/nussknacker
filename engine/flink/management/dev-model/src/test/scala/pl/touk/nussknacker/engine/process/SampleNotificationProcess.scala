package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.management.sample.UnitTestsProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

class SampleNotificationProcess extends AnyFlatSpec with Matchers with FlinkSpec {

  import spel.Implicits._

  val creator = new UnitTestsProcessConfigCreator

  it should "serialize and run" in {
    val process =
      ScenarioBuilder
        .streaming("sample_notification")
        .parallelism(1)
        .source("start", "Notifications", "ratePerMinute" -> "3")
        .emptySink("end", "KafkaSink")

    val config = ConfigFactory.load()

    val modelData = LocalModelData(config, creator, List.empty)
    val env       = flinkMiniCluster.createExecutionEnvironment()
    FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
      .register(env, process, ProcessVersion.empty, DeploymentData.empty)

    env.withJobRunning(process.id) {}
  }

}
