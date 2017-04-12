package pl.touk.esp.engine.process

import com.typesafe.config.ConfigFactory
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.management.sample.DemoProcessConfigCreator
import pl.touk.esp.engine.spel

import scala.concurrent.Future

class SampleNotificationProcess extends FlatSpec with Matchers {

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global

  val creator = new DemoProcessConfigCreator
  val env = StreamExecutionEnvironment.createLocalEnvironment()

  it should "serialize and run" in {
    val process =
      EspProcessBuilder
        .id("sample_notification")
        .parallelism(1)
        .exceptionHandler("topic" -> "errors")
        .source("start", "Notifications", "ratePerMinute" -> "3")
        .sink("end", "#input", "KafkaSink")

    val config = ConfigFactory.load()

    FlinkProcessRegistrar(creator, config).register(env, process)

    Future { env.execute("sample_notification")}.failed.foreach(_.printStackTrace())
    Thread.sleep(2000)
  }
}
