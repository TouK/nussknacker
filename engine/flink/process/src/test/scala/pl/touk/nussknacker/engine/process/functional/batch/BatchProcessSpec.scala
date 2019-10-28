package pl.touk.nussknacker.engine.process.functional.batch

import java.nio.file.{Files, Paths}
import java.util.Date

import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.{BatchProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.process.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.ProcessTestHelpers.{BatchSinkForStrings, SimpleRecord}
import pl.touk.nussknacker.engine.spel

class BatchProcessSpec extends FunSuite with Matchers with BeforeAndAfter {

  import spel.Implicits._

  private val data = List(
    SimpleRecord(id = "1", value1 = 10L, value2 = "10", date = new Date(10)),
    SimpleRecord(id = "1", value1 = 20L, value2 = "20", date = new Date(20))
  )

  after {
    BatchSinkForStrings.clear()
  }

  test("should forward input to output") {
    val process = BatchProcessBuilder.id("inputToOutput")
      .exceptionHandler()
      .source("input", "batchInput")
      .sink("sink", "#input", "batchSinkForStrings")

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    BatchSinkForStrings.data should have size 2
    BatchSinkForStrings.data shouldBe data.map(_.toString)
  }


  test("should forward file input to output") {
    import scala.collection.JavaConverters._
    val inputFile = Files.createTempFile("inputToOutput", ".txt")
    val outputFile = inputFile.toString + ".output"
    Files.write(inputFile, (1 to 10).map(_.toString).asJava)
    val process = BatchProcessBuilder.id("inputToOutput")
      .exceptionHandler()
      .source("source", "batchTextLineSource", "path" -> s"'$inputFile'")
      .sink("sink", "#input", "batchTextLineSink", "path" -> s"'$outputFile'")

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    Files.readAllLines(Paths.get(outputFile)).asScala shouldBe (1 to 10).map(_.toString)
  }

  test("should extract single field") {
    val process = BatchProcessBuilder.id("extractSingleField")
      .exceptionHandler()
      .source("input", "batchInput")
      .sink("sink", "#input.value2", "batchSinkForStrings")

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    BatchSinkForStrings.data should have size 2
    BatchSinkForStrings.data shouldBe data.map(_.value2)
  }

  test("should filter records") {
    val process = BatchProcessBuilder.id("filterTest")
      .exceptionHandler()
      .source("input", "batchInput")
      .filter("value greater than 10", "#input.value1 > 10")
      .sink("sink", "#input", "batchSinkForStrings")

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    BatchSinkForStrings.data should have size 1
    BatchSinkForStrings.data shouldBe data.filter(_.value1 > 10L).map(_.toString)
  }

  test("should switch records") {
    val process = BatchProcessBuilder.id("filterTest")
      .exceptionHandler()
      .source("input", "batchInput")
      .switch("id value", "#input.id", "id",
        GraphBuilder.sink("sinkOther", "'other'", "batchSinkForStrings"),
        Case("#id == '1'", GraphBuilder.sink("sinkEq1", "'eq1'", "batchSinkForStrings")),
        Case("#id == '2'", GraphBuilder.sink("sinkEq2", "'eq2'", "batchSinkForStrings")))
    val data = List(
      SimpleRecord(id = "1", value1 = 10L, value2 = "10", date = new Date(10)),
      SimpleRecord(id = "2", value1 = 20L, value2 = "20", date = new Date(20)),
      SimpleRecord(id = "3", value1 = 30L, value2 = "30", date = new Date(30)),
      SimpleRecord(id = "4", value1 = 40L, value2 = "40", date = new Date(40))
    )

    ProcessTestHelpers.processInvoker.invokeBatch(process, data)

    BatchSinkForStrings.data should have size 4
    BatchSinkForStrings.data should contain theSameElementsAs List("eq1", "eq2", "other", "other")
  }
}
