package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.management.sample.UnitTestsProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

class SampleRecommendationProcess extends FlatSpec with FlinkSpec with Matchers {

  import spel.Implicits._

  private val creator = new UnitTestsProcessConfigCreator

  it should "serialize process and run" in {
    val process =
      EspProcessBuilder
        .id("sample")
        .parallelism(1)
        .exceptionHandler("topic" -> "'errors'")
        .source("start", "PageVisits", "ratePerMinute" -> "3")
        .sink("end", "#input", "Recommend")

    val config = ConfigFactory.load()

    val env = flinkMiniCluster.createExecutionEnvironment()
    val modelData = LocalModelData(config, creator)
    FlinkProcessRegistrar(new FlinkProcessCompiler(modelData), config, ExecutionConfigPreparer.unOptimizedChain(modelData))
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty)

    env.withJobRunning(process.id) {}
  }

}
