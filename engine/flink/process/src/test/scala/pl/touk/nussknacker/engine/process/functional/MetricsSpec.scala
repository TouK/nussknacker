package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers.processInvoker
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord, SinkForStrings}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.VeryPatientScalaFutures


class MetricsSpec extends FlatSpec with Matchers with VeryPatientScalaFutures with BeforeAndAfterEach {

  override protected def beforeEach(): Unit = {
    TestReporter.reset()
  }

  it should "measure time for service" in {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    invoke(process, data)

    MockService.data shouldNot be('empty)
    val histogram = TestReporter.taskManagerReporter.testHistogram("serviceTimes.mockService.OK")
    histogram.getCount shouldBe 1

  }

  it should "measure errors" in {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "1 / #input.value1")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 0, "a", new Date(0))
    )
    invoke(process, data)

    eventually {
      val totalGauges = TestReporter.taskManagerReporter.testGauges("error.instantRate")
      totalGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true

      val nodeGauges = TestReporter.taskManagerReporter.testGauges("error.proc2.instantRateByNode")
      nodeGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true
    }

  }

  it should "measure node counts" in {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("source1", "input")
      .filter("filter1", "#input.value1 == 10")
      .split("split1",
        GraphBuilder.emptySink("out2", "monitor"),
        GraphBuilder
          .processor("proc2", "logService", "all" -> "#input.value2")
          .emptySink("out", "monitor")
      )


    val data = List(
      SimpleRecord("1", 12, "a", new Date(0)),
      SimpleRecord("1", 10, "a", new Date(0))
    )

    invoke(process, data)

    def counter(name: String) =
      TestReporter.taskManagerReporter.testCounters(name).map(_.getCount).find(_ > 0).getOrElse(0)

    eventually {
      counter("nodeCount.source1") shouldBe 2L
      counter("nodeCount.filter1") shouldBe 2L
      counter("nodeCount.split1") shouldBe 1L
      counter("nodeCount.proc2") shouldBe 1L
      counter("nodeCount.out") shouldBe 1L
      counter("nodeCount.out2") shouldBe 1L
    }
  }

  it should "open measuring service" in {
    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .enricher("enricher1", "outputValue", "enricherWithOpenService")
      .sink("out", "#outputValue", "sinkForStrings")

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    invoke(process, data)
    eventually {
      SinkForStrings.data shouldBe List("initialized!")
    }
  }

  private def invoke(process: EspProcess, data: List[SimpleRecord]) = {
    processInvoker.invokeWithSampleData(process, data, TestReporterUtil.configWithTestMetrics())
  }
}
