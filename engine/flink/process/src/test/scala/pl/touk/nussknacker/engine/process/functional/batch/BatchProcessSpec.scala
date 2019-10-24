package pl.touk.nussknacker.engine.process.functional.batch

import java.util.Date

import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.process.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.{BatchSinkForStrings, SimpleRecord}
import pl.touk.nussknacker.engine.spel

class BatchProcessSpec extends FunSuite with Matchers with BeforeAndAfter {

  import spel.Implicits._

  after {
    BatchSinkForStrings.clear()
  }

  test("should forward input to output") {
    val process = EspProcessBuilder.id("inputToOutput")
      .exceptionHandler()
      .source("input", "batchInput")
      .sink("sink", "#input", "batchSinkForStrings")
    val data = List(
      SimpleRecord(id = "1", value1 = 10L, value2 = "10", date = new Date(10)),
      SimpleRecord(id = "1", value1 = 20L, value2 = "20", date = new Date(20))
    )

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    BatchSinkForStrings.data should have size 2
    BatchSinkForStrings.data shouldBe data.map(_.toString)
  }

  test("should extract single field") {
    val process = EspProcessBuilder.id("extractSingleField")
      .exceptionHandler()
      .source("input", "batchInput")
      .sink("sink", "#input.value2", "batchSinkForStrings")

    val data = List(
      SimpleRecord(id = "1", value1 = 10L, value2 = "10", date = new Date(10)),
      SimpleRecord(id = "1", value1 = 20L, value2 = "20", date = new Date(20))
    )

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    BatchSinkForStrings.data should have size 2
    BatchSinkForStrings.data shouldBe data.map(_.value2)
  }

  test("should filter records") {
    val process = EspProcessBuilder.id("filterTest")
      .exceptionHandler()
      .source("input", "batchInput")
      .filter("value greater than 10", "#input.value1 > 10")
      .sink("sink", "#input", "batchSinkForStrings")

    val data = List(
      SimpleRecord(id = "1", value1 = 10L, value2 = "10", date = new Date(10)),
      SimpleRecord(id = "1", value1 = 20L, value2 = "20", date = new Date(20))
    )

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    BatchSinkForStrings.data should have size 1
    BatchSinkForStrings.data shouldBe data.filter(_.value1 > 10L).map(_.toString)
  }
}
