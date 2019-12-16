package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest._
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel

import scala.util.Try

class SqlProcessItTests extends FunSuite with BeforeAndAfterAll with Matchers {

  import spel.Implicits._

  import scala.collection.JavaConverters._

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

    processInvoker.invokeWithSampleData(process, List(SimpleRecord("1", 12, "a", new Date(1))))

    val list = MockService.data.asInstanceOf[List[java.util.List[TypedMap]]]
    val result = list.flatMap(_.asScala.toList).flatMap(_.fields.mapValues(parseNumber))
    val pair = ("VALUE1", BigDecimal(12))
    result shouldEqual List(pair, pair, pair)
  }

  test("run complex sql process") {
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
      .buildSimpleVariable("value3List", "value3List", "SELECT value3 FROM list".asSql)
      .buildSimpleVariable("anotherValue3List", "anotherValue3List", "SELECT value3 FROM value3List".asSql)
      .processor("proc1", "logService", "all" -> "#fromOne")
      .processor("proc2", "logService", "all" -> "#anotherValue3List")
      .processorEnd("proc3", "logService", "all" -> "#sumOne")

    processInvoker.invokeWithSampleData(process, List(SimpleRecord(id = "1", value1 = 12, value2 = "a", date = new Date(1), value3 = BigDecimal(1.51))))

    val list = MockService.data.asInstanceOf[List[java.util.List[TypedMap]]]
    val result = list.flatMap(_.asScala.toList).flatMap(_.fields).toMap.mapValues(parseNumber)
    result shouldBe Map(
      "ONE" -> BigDecimal(1),
      "SUM_VALUE" -> BigDecimal(2),
      "VALUE3" -> BigDecimal(1.51)
    )
  }

  private def parseNumber(a: Any): BigDecimal = {
    val s = a.toString
    Try(s.toLong).map(BigDecimal.apply)
      .getOrElse(BigDecimal.apply(s.toDouble))
  }

}
