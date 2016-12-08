package pl.touk.esp.engine.process.runner

import java.util.Date

import argonaut.PrettyParams
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.api.deployment.test.{InvocationResult, NodeResult, TestData}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.process.ProcessTestHelpers.SimpleRecord
import pl.touk.esp.engine.spel

class FlinkTestMainSpec extends FlatSpec with Matchers with Inside {


  import spel.Implicits._

  val ProcessMarshaller = new ProcessMarshaller

  it should "be able to compile and serialize services" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .filter("filter1", "#input.value1 > 1")
        .buildSimpleVariable("v1", "variable1", "'ala'")
        .processor("proc2", "logService", "all" -> "#input.id")
        .sink("out", "#input.value1", "monitor")

    val input = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
    val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

    val results = FlinkTestMain.run(ProcessMarshaller.toJson(process, PrettyParams.spaces2),
      ConfigFactory.load(), TestData(List("0|1|2|3|4|5|6", "0|11|2|3|4|5|6")), List())

    val nodeResults = results.nodeResults

    nodeResults("id") shouldBe List(nodeResult("input" -> input), nodeResult("input" -> input2))
    nodeResults("filter1") shouldBe List(nodeResult("input" -> input), nodeResult("input" -> input2))
    nodeResults("v1") shouldBe List(nodeResult("input" -> input2))
    nodeResults("proc2") shouldBe List(nodeResult("input" -> input2, "variable1" -> "ala"))
    nodeResults("out") shouldBe List(nodeResult("input" -> input2, "variable1" -> "ala"))

    val invocationResults = results.invocationResults

    invocationResults("proc2") shouldBe List(InvocationResult(Context(Map("input" -> input2, "variable1" -> "ala")), Map("all" -> "0")))
    invocationResults("out") shouldBe List(InvocationResult(Context(Map("input" -> input2, "variable1" -> "ala")), Map("output" -> 11)))


  }

  def nodeResult(vars: (String, Any)*) = NodeResult(Context(Map(vars: _*)))

}

