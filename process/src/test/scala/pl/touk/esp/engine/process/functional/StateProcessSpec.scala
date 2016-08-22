package pl.touk.esp.engine.process.functional

import java.util.Date

import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.build.GraphBuilder
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.service.{Parameter, ServiceRef}
import pl.touk.esp.engine.process.ProcessTestHelpers.{MockService, processInvoker, SimpleRecord}
import pl.touk.esp.engine.spel

import scala.concurrent.duration._

class StateProcessSpec extends FlatSpec with Matchers {

  it should "fire alert when aggregate threshold exceeded" in {
    import spel.Implicits._

    val process = EspProcess(MetaData("proc1"),
      GraphBuilder.source("id", "input")
        .aggregate("agg", "input", "#input.id", 5 seconds, 5 second,
          Some("#input.value1 > 24"), Some("simpleFoldingFun"))
        .processor("proc2", ServiceRef("logService", List(Parameter("all", "#input.value2"))))
        .sink("out", "monitor"))
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
