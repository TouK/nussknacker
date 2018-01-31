package pl.touk.nussknacker.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.management.sample.DemoProcessConfigCreator
import pl.touk.nussknacker.engine.process.compiler.StandardFlinkProcessCompiler
import pl.touk.nussknacker.engine.spel

import scala.concurrent.Future

class SampleRecomendationProcess extends FlatSpec with Matchers {

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global

  val creator = new DemoProcessConfigCreator
  val env = StreamExecutionEnvironment.createLocalEnvironment(1, FlinkTestConfiguration.configuration)

  it should "serialize and run" in {
    val process =
      EspProcessBuilder
        .id("sample")
        .parallelism(1)
        .exceptionHandler("topic" -> "'errors'")
        .source("start", "PageVisits", "ratePerMinute" -> "3")
        .sink("end", "#input", "Recommend")

    val config = ConfigFactory.load()

    new StandardFlinkProcessCompiler(creator, config).createFlinkProcessRegistrar().register(env, process)

    Future {
      env.getConfig.disableSysoutLogging
      env.execute("sample")
    }.failed.foreach(_.printStackTrace())
    Thread.sleep(2000)
  }
}
