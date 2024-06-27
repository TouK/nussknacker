package pl.touk.nussknacker.engine.process.functional

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.process.SimpleJavaEnum
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.SimpleRecord

import java.util.Date

class DictsSpec extends AnyFunSuite with Matchers with ProcessTestHelpers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("use dicts in indexer with enum values") {
    checkProcess("#input.enumValue == #enum['ONE']")
  }

  test("use dicts as property with enum values") {
    checkProcess("#input.enumValue == #enum.ONE")
  }

  private def checkProcess(filterExpression: String) = {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "input")
      .filter("filter", filterExpression.spel)
      .processorEnd("proc2", "logService", "all" -> "#input".spel)

    val data = List(
      SimpleRecord("1", 3, "fooId", new Date(0), enumValue = SimpleJavaEnum.ONE),
      SimpleRecord("1", 5, "invalidId", new Date(1000), enumValue = SimpleJavaEnum.TWO)
    )

    processInvoker.invokeWithSampleData(process, data)

    ProcessTestHelpers.logServiceResultsHolder.results should have size 1
  }

}
