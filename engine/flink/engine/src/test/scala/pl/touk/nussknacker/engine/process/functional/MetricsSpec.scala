package pl.touk.nussknacker.engine.process.functional

import java.util.Date

import org.apache.flink.configuration.Configuration
import org.scalatest.{BeforeAndAfterEach, FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.process.helpers.ProcessTestHelpers
import pl.touk.nussknacker.engine.process.helpers.SampleNodes.{MockService, SimpleRecord, SinkForStrings}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.test.VeryPatientScalaFutures

class MetricsSpec extends FunSuite with Matchers with VeryPatientScalaFutures with ProcessTestHelpers with BeforeAndAfterEach {

  override protected def beforeEach(): Unit = {
    TestReporter.reset(this.getClass.getName)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    TestReporter.remove(this.getClass.getName)
  }

  test("measure time for service") {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "#input.value2")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)

    MockService.data shouldNot be('empty)
    val histogram = TestReporter.get(this.getClass.getName).testHistogram("service.OK.serviceName.mockService.histogram")
    histogram.getCount shouldBe 1

  }

  test("measure errors") {

    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .processor("proc2", "logService", "all" -> "1 / #input.value1")
      .emptySink("out", "monitor")
    val data = List(
      SimpleRecord("1", 0, "a", new Date(0))
    )
    processInvoker.invokeWithSampleData(process, data)

    eventually {
      val reporter = TestReporter.get(this.getClass.getName)

      val totalGauges = reporter.testGauges("error.instantRate.instantRate")
      totalGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true

      val nodeGauges = reporter.testGauges("error.instantRateByNode.nodeId.proc2.instantRate")
      nodeGauges.exists(_.getValue.asInstanceOf[Double] > 0) shouldBe true

      val nodeCounts = reporter.testCounters("error.instantRateByNode.nodeId.proc2")
      nodeCounts.exists(_.getCount > 0) shouldBe true
    }

  }

  test("measure node counts") {

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

    processInvoker.invokeWithSampleData(process, data)

    def counter(name: String) =
      TestReporter.get(this.getClass.getName).testCounters(name).map(_.getCount).find(_ > 0).getOrElse(0)

    eventually {
      counter("nodeId.source1.nodeCount") shouldBe 2L
      counter("nodeId.filter1.nodeCount") shouldBe 2L
      counter("nodeId.split1.nodeCount") shouldBe 1L
      counter("nodeId.proc2.nodeCount") shouldBe 1L
      counter("nodeId.out.nodeCount") shouldBe 1L
      counter("nodeId.out2.nodeCount") shouldBe 1L
    }
  }

  test("open measuring service"){
    import spel.Implicits._

    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "input")
      .enricher("enricher1", "outputValue", "enricherWithOpenService")
      .emptySink("out", "sinkForStrings", "value" -> "#outputValue")

    val data = List(
      SimpleRecord("1", 12, "a", new Date(0))
    )

    processInvoker.invokeWithSampleData(process, data)
    eventually {
      SinkForStrings.data shouldBe List("initialized!")
    }
  }

  override protected def prepareFlinkConfiguration(): Configuration = {
    TestReporterUtil.configWithTestMetrics(new Configuration(), this.getClass.getName)
  }

}
