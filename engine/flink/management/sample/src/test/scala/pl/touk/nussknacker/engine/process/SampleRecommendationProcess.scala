package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.{FlinkTestConfiguration, StoppableExecutionEnvironment}
import pl.touk.nussknacker.engine.management.sample.UnitTestsProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompiler
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.LocalModelData

class SampleRecommendationProcess extends FlatSpec with BeforeAndAfterAll with Matchers {

  import spel.Implicits._

  private val env: StoppableExecutionEnvironment = StoppableExecutionEnvironment(FlinkTestConfiguration.configuration())

  private val creator = new UnitTestsProcessConfigCreator

  override protected def afterAll(): Unit = {
    super.afterAll()
    env.stop()
  }

  it should "serialize process and run" in {
    val process =
      EspProcessBuilder
        .id("sample")
        .parallelism(1)
        .exceptionHandler("topic" -> "'errors'")
        .source("start", "PageVisits", "ratePerMinute" -> "3")
        .sink("end", "#input", "Recommend")

    val config = ConfigFactory.load()


    FlinkStreamingProcessRegistrar(new FlinkProcessCompiler(LocalModelData(config, creator)), config)
      .register(new StreamExecutionEnvironment(env), process, ProcessVersion.empty)

    val result = env.execute(process.id)

    env.runningJobs().exists(_ == result.getJobID) shouldBe true

  }
}
