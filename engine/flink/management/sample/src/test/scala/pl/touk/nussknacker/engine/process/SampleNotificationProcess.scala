package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.management.sample.UnitTestsProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

class SampleNotificationProcess extends FlatSpec with Matchers with FlinkSpec {

  import spel.Implicits._

  val creator = new UnitTestsProcessConfigCreator

  it should "serialize and run" in {
    val process =
      EspProcessBuilder
        .id("sample_notification")
        .parallelism(1)
        .exceptionHandler()
        .source("start", "Notifications", "ratePerMinute" -> "3")
        .sink("end", "#input", "KafkaSink")

    val config = ConfigFactory.load()

    val modelData = LocalModelData(config, creator)
    val env = flinkMiniCluster.createExecutionEnvironment()
    FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), ExecutionConfigPreparer.unOptimizedChain(modelData))
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty, DeploymentData.empty)

    env.withJobRunning(process.id) {}
  }

}
