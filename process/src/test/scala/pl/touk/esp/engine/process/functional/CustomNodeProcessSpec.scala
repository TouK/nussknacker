package pl.touk.esp.engine.process.functional


import java.util.Date

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.esp.engine.spel

class CustomNodeProcessSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "fire alert when aggregate threshold exceeded" in {

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .customNode("custom", "outRec", "stateCustom", "keyBy" -> "#input.id")
      .filter("delta", "#outRec.record.value1 > #outRec.previous + 5")
      .processor("proc2", "logService", "all" -> "#outRec.record.value1")
      .sink("out", "monitor")

    val data = List(
      SimpleRecord("1", 3, "a", new Date(0)),
      SimpleRecord("1", 5, "b", new Date(1000)),
      SimpleRecord("1", 12, "d", new Date(4000)),
      SimpleRecord("1", 14, "d", new Date(10000)),
      SimpleRecord("1", 20, "d", new Date(10000))

    )

    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    processInvoker.invoke(process, data, env)

    MockService.data.toList shouldBe List(12L, 20L)
  }

}
