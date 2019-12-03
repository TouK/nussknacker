package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.management.sample.DemoProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkStreamingProcessCompiler
import pl.touk.nussknacker.engine.spel

class SampleNotificationProcess extends FlatSpec with Matchers {

  import spel.Implicits._

  val creator = new DemoProcessConfigCreator
  val env = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())

  it should "serialize and run" in {
    val process =
      EspProcessBuilder
        .id("sample_notification")
        .parallelism(1)
        .exceptionHandler("topic" -> "'errors'")
        .source("start", "Notifications", "ratePerMinute" -> "3")
        .sink("end", "#input", "KafkaSink")

    val config = ConfigFactory.load()

    new FlinkStreamingProcessCompiler(creator, config).createFlinkProcessRegistrar()
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty)

    val result = env.execute(process.id)
    env.runningJobs().exists(_ == result.getJobID) shouldBe true

  }
}
