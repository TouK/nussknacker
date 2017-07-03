package pl.touk.esp.engine.process.functional

import java.util.Date

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.esp.engine.spel

import scala.concurrent.duration._

class StateProcessSpec extends FlatSpec with Matchers {

  //FIXME: brak testu
  ignore should "fire alert when aggregate threshold exceeded" in {
    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .sink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 15, "b", new Date(1000)),
      SimpleRecord("1", 23, "d", new Date(4000))
    )

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    MockService.data shouldNot be('empty)
    MockService.data(0) shouldBe Set("a", "b")
  }

}
