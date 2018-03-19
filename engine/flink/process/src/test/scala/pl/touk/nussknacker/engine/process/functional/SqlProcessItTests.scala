package pl.touk.nussknacker.engine.process.functional

import java.nio.charset.StandardCharsets
import java.util.Date

import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.{MockService, SimpleRecord, processInvoker}
import pl.touk.nussknacker.engine.spel

class SqlProcessItTests extends FlatSpec with BeforeAndAfterAll with Matchers with Eventually {
  private implicit class SqlExpression(expression: String) {

    def asSql: Expression =
      Expression(
        language = "sql",
        expression = expression
      )
  }

  it should "measure time for service" in {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .buildSimpleVariable("a", "list", "{#input, #input, #input}")
      .buildSimpleVariable("b", "twoValues", "SELECT value1, date FROM list".asSql)
      .buildSimpleVariable("c", "value1", "SELECT value1 FROM twoValues".asSql)
      .processor("proc2", "logService", "all" -> "#value1")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(1))
    )
    import scala.collection.JavaConversions._
    invoke(process, data)
    val list = MockService.data.asInstanceOf[List[java.util.List[TypedMap]]]
    val result = for {
      javaList <- list
      typedMap <- javaList.toList
      (key, value) <- typedMap.fields.toList
    } yield (key, value.asInstanceOf[java.math.BigDecimal].toBigInteger.intValue())
    val pair = ("VALUE1", 12)
    result shouldEqual List(pair, pair, pair)
  }

  private def invoke(process: EspProcess, data: List[SimpleRecord]) = {
    processInvoker.invoke(process, data, ProcessVersion.empty, 1, TestReporterUtil.configWithTestMetrics())
  }
}
