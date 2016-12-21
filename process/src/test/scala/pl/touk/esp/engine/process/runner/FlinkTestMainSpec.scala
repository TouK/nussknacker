package pl.touk.esp.engine.process.runner

import java.util.Date

import argonaut.PrettyParams
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.esp.engine.api.Context
import pl.touk.esp.engine.api.deployment.test.{ExpressionInvocationResult, NodeResult, TestData}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.process.ProcessTestHelpers.{SimpleRecord, SimpleRecordWithPreviousValue}
import pl.touk.esp.engine.spel

class FlinkTestMainSpec extends FlatSpec with Matchers with Inside {


  import spel.Implicits._

  val ProcessMarshaller = new ProcessMarshaller

  it should "be able to return test results" in {
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

    nodeResults("id") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
    nodeResults("filter1") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))
    nodeResults("v1") shouldBe List(nodeResult(1, "input" -> input2))
    nodeResults("proc2") shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))
    nodeResults("out") shouldBe List(nodeResult(1, "input" -> input2, "variable1" -> "ala"))

    val invocationResults = results.invocationResults

    invocationResults("proc2") shouldBe
      List(ExpressionInvocationResult(Context("proc1-id-0-1", Map("input" -> input2, "variable1" -> "ala")), "all", "0"))
    invocationResults("out") shouldBe
      List(ExpressionInvocationResult(Context("proc1-id-0-1",Map("input" -> input2, "variable1" -> "ala")), "expression", 11))


  }

  it should "return correct result for custom node" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .customNode("cid", "out", "stateCustom", "keyBy" -> "#input.id", "stringVal" -> "'s'")
        .sink("out", "#input.value1 + ' ' + #out.previous", "monitor")

    val input = SimpleRecord("0", 1, "2", new Date(3), Some(4), 5, "6")
    val input2 = SimpleRecord("0", 11, "2", new Date(3), Some(4), 5, "6")

    val aggregate = SimpleRecordWithPreviousValue(input, 0, "s")
    val aggregate2 = SimpleRecordWithPreviousValue(input2, 1, "s")


    val results = FlinkTestMain.run(ProcessMarshaller.toJson(process, PrettyParams.spaces2),
      ConfigFactory.load(), TestData(List("0|1|2|3|4|5|6", "0|11|2|3|4|5|6")), List())

    val nodeResults = results.nodeResults

    nodeResults("id") shouldBe List(nodeResult(0, "input" -> input), nodeResult(1, "input" -> input2))

    val resultsAfterCid = List(
      nodeResult(0, "input" -> input, "out" -> aggregate),
      nodeResult(1, "input" -> input2, "out" -> aggregate2))

    nodeResults("cid") shouldBe resultsAfterCid
    nodeResults("out") shouldBe resultsAfterCid

    val invocationResults = results.invocationResults

    invocationResults("cid") shouldBe
      List(
        ExpressionInvocationResult(Context("", Map()), "stringVal", "s"),
        ExpressionInvocationResult(Context("proc1-id-0-0", Map("input" -> input)), "keyBy", "0"),
        ExpressionInvocationResult(Context("proc1-id-0-1", Map("input" -> input2)), "keyBy", "0")
      )
    invocationResults("out") shouldBe
      List(
        ExpressionInvocationResult(Context("proc1-id-0-0",Map("input" -> input, "out" -> aggregate)), "expression", "1 0"),
        ExpressionInvocationResult(Context("proc1-id-0-1",Map("input" -> input2, "out" -> aggregate2)), "expression", "11 1")
      )

  }

  it should "handle large parallelism" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .parallelism(4)
        .exceptionHandler()
        .source("id", "input")
        .sink("out", "#input", "monitor")

    val results = FlinkTestMain.run(ProcessMarshaller.toJson(process, PrettyParams.spaces2),
      ConfigFactory.load(), TestData(List("0|1|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6", "0|11|2|3|4|5|6")), List())

    val nodeResults = results.nodeResults

    nodeResults("id") should have length 5

  }

  def nodeResult(count: Int, vars: (String, Any)*) = NodeResult(Context(s"proc1-id-0-$count", Map(vars: _*)))

}

