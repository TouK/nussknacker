package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.concurrent.Eventually
import org.scalatest._
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.nussknacker.engine.spel

import scala.util.Try

class SqlProcessItTests extends FunSuite with BeforeAndAfterAll with Matchers with Eventually {

  import spel.Implicits._
  import scala.collection.JavaConversions._

  private implicit class SqlExpression(expression: String) {

    def asSql: Expression =
      Expression(
        language = "sql",
        expression = expression
      )
  }

  test("run sql process") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("a", "list", "{#input, #input, #input}")
      .buildSimpleVariable("b", "twoValues", "SELECT value1, date FROM list".asSql)
      .buildSimpleVariable("c", "value1", "SELECT value1 FROM twoValues".asSql)
      .processor("proc2", "logService", "all" -> "#value1")
      .emptySink("out", "monitor")

    invoke(process, List(SimpleRecord("1", 12, "a", new Date(1))))

    val list = MockService.data.asInstanceOf[List[java.util.List[TypedMap]]]
    val result = list.flatMap(_.toList).flatMap(_.fields.mapValues(parseNumber))
    val pair = ("VALUE1", BigDecimal(12))
    result shouldEqual List(pair, pair, pair)
  }

  test("define constant as column in sql") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("a", "list", "{#input}")
      .buildSimpleVariable("b", "foo", "SELECT value1, 1 as one FROM list where value3Opt is null".asSql)
      .buildSimpleVariable("c", "fromOne", "SELECT f.one FROM foo f".asSql)
      .buildSimpleVariable("d", "sumOne",
      """SELECT (SUM(case
        |     when value1 = 12 then 2
        |     else 0
        |     end
        |)) AS sum_value from list
      """.stripMargin
        .asSql
    )
      .processor("proc1", "logService", "all" -> "#fromOne")
      .processorEnd("proc2", "logService", "all" -> "#sumOne")

    invoke(process, List(SimpleRecord("1", 12, "a", new Date(1))))

    val list = MockService.data.asInstanceOf[List[java.util.List[TypedMap]]]
    val result = list.flatMap(_.toList).flatMap(_.fields).toMap.mapValues(parseNumber)
    result shouldBe Map(
      "ONE" -> BigDecimal(1),
      "SUM_VALUE" -> BigDecimal(2)
    )
  }

  private def parseNumber(a: Any): BigDecimal = {
    val s = a.toString
    Try(s.toLong).map(BigDecimal.apply)
      .getOrElse(BigDecimal.apply(s.toDouble))
  }

  private def invoke(process: EspProcess, data: List[SimpleRecord]) = {
    processInvoker.invoke(process, data, ProcessVersion.empty, 1, TestReporterUtil.configWithTestMetrics())
  }
}
