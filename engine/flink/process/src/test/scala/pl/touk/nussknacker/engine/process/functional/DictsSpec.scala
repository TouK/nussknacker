package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord}
import pl.touk.nussknacker.engine.spel

class DictsSpec extends FunSuite with Matchers {

  import spel.Implicits._

  test("use dicts in indexer with enum values") {
    checkProcess("#input.enumValue == #enum['ONE']")
  }

  test("use dicts as property with enum values") {
    checkProcess("#input.enumValue == #enum.ONE")
  }

  private def checkProcess(filterExpression: String) = {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .filter("filter", filterExpression)
      .processorEnd("proc2", "logService", "all" -> "#input")

    val data = List(
      SimpleRecord("1", 3, "fooId", new Date(0), enumValue = SimpleJavaEnum.ONE),
      SimpleRecord("1", 5, "invalidId", new Date(1000), enumValue = SimpleJavaEnum.TWO))

    processInvoker.invokeWithSampleData(process, data)

    MockService.data should have size 1
  }

}
