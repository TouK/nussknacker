package pl.touk.nussknacker.engine.process.functional.batch

import java.nio.file.{Files, Paths}
import java.util.Date

import org.scalatest.{BeforeAndAfter, FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.{BatchProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.node.Case
import pl.touk.nussknacker.engine.process.helpers.BatchProcessTestHelpers.{RecordingExceptionHandler, SimpleRecord, SinkForStrings, processInvoker}
import pl.touk.nussknacker.engine.spel

class BatchProcessSpec extends FunSuite with Matchers with BeforeAndAfter {

  import spel.Implicits._

  private val data = List(
    SimpleRecord(id = "1", value1 = 10L, value2 = "10", date = new Date(10)),
    SimpleRecord(id = "1", value1 = 20L, value2 = "20", date = new Date(20))
  )

  after {
    SinkForStrings.clear()
  }

  test("should forward input to output") {
    val process = BatchProcessBuilder.id("inputToOutput")
      .exceptionHandler()
      .source("input", "input")
      .sink("sink", "#input", "sinkForStrings")

    processInvoker.invoke(process, data)

    SinkForStrings.data should have size 2
    SinkForStrings.data shouldBe data.map(_.toString)
  }

  test("should forward file input to output") {
    import scala.collection.JavaConverters._
    val inputFile = Files.createTempFile("inputToOutput", ".txt")
    val outputFile = inputFile.toString + ".output"
    Files.write(inputFile, (1 to 10).map(_.toString).asJava)
    val process = BatchProcessBuilder.id("inputToOutput")
      .exceptionHandler()
      .source("source", "textLineSource", "path" -> s"'$inputFile'")
      .sink("sink", "#input", "textLineSink", "path" -> s"'$outputFile'")

    processInvoker.invoke(process, data)

    Files.readAllLines(Paths.get(outputFile)).asScala shouldBe (1 to 10).map(_.toString)
  }

  test("should extract single field") {
    val process = BatchProcessBuilder.id("extractSingleField")
      .exceptionHandler()
      .source("input", "input")
      .sink("sink", "#input.value2", "sinkForStrings")

    processInvoker.invoke(process, data)

    SinkForStrings.data should have size 2
    SinkForStrings.data shouldBe data.map(_.value2)
  }

  test("should filter records") {
    val process = BatchProcessBuilder.id("filterTest")
      .exceptionHandler()
      .source("input", "input")
      .filter("value greater than 10", "#input.value1 > 10")
      .sink("sink", "#input", "sinkForStrings")

    processInvoker.invoke(process, data)

    SinkForStrings.data should have size 1
    SinkForStrings.data shouldBe data.filter(_.value1 > 10L).map(_.toString)
  }

  test("should switch records") {
    val process = BatchProcessBuilder.id("filterTest")
      .exceptionHandler()
      .source("input", "input")
      .switch("id value", "#input.id", "id",
        GraphBuilder.sink("sinkOther", "'other'", "sinkForStrings"),
        Case("#id == '1'", GraphBuilder.sink("sinkEq1", "'eq1'", "sinkForStrings")),
        Case("#id == '2'", GraphBuilder.sink("sinkEq2", "'eq2'", "sinkForStrings")))
    val data = List(
      SimpleRecord(id = "1", value1 = 10L, value2 = "10", date = new Date(10)),
      SimpleRecord(id = "2", value1 = 20L, value2 = "20", date = new Date(20)),
      SimpleRecord(id = "3", value1 = 30L, value2 = "30", date = new Date(30)),
      SimpleRecord(id = "4", value1 = 40L, value2 = "40", date = new Date(40))
    )

    processInvoker.invoke(process, data)

    SinkForStrings.data should have size 4
    SinkForStrings.data should contain theSameElementsAs List("eq1", "eq2", "other", "other")
  }

  test("should handle exceptions") {
    val process = BatchProcessBuilder.id("inputToOutput")
      .exceptionHandler()
      .source("input", "input")
      .sink("sink", "#input.value1 / 0", "sinkForStrings")

    processInvoker.invoke(process, data)

    RecordingExceptionHandler.data should have size 2
    RecordingExceptionHandler.data.map(_.throwable.getCause) forall { _.isInstanceOf[ArithmeticException] } shouldBe true
  }

  // TODO: custom nodes
  // TODO: joins
  // TODO: metrics
}
