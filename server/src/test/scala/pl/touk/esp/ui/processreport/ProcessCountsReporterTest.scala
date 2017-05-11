package pl.touk.esp.ui.processreport

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.node
import pl.touk.esp.engine.spel
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.process.report.influxdb.ProcessBaseCounts

class ProcessCountsReporterTest extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "count events at every node for process1" in {
    val counts = ProcessBaseCounts(3, Map("end1" -> 1), Map("filter1" -> 2), Map.empty)
    val process = EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", "")
      .filter("filter1", "1 > 0")
      .enricher("enricher1", "a", "")
      .sink("end1", "")
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), ProcessingType.Streaming)
    val counter = new ProcessCountsReporter()

    counter.reportCounts(displayable, counts) shouldBe Map("source" -> 3, "filter1" -> 3, "enricher1" -> 1, "end1" -> 1)
  }

  it should "count events at every node for process2" in {
    val counts = ProcessBaseCounts(5, Map("end1" -> 1), Map("filter1" -> 2, "filter2" -> 2), Map.empty)
    val process = EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", "")
      .filter("filter1", "")
      .enricher("enricher1", "a", "")
      .filter("filter2", "")
      .enricher("enricher2", "a", "")
      .sink("end1", "")
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), ProcessingType.Streaming)
    val counter = new ProcessCountsReporter()

    counter.reportCounts(displayable, counts) shouldBe Map(
      "source" -> 5,
      "filter1" -> 5,
      "enricher1" -> 3,
      "filter2" -> 3,
      "enricher2" -> 1,
      "end1" -> 1
    )
  }

  it should "count events at every node for process3" in {
    val counts = ProcessBaseCounts(5, Map("end1" -> 1, "end2" -> 2), Map("filter1" -> 2), Map.empty)
    val process = EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", "")
      .filter("filter1", "")
      .enricher("enricher1", "a", "")
      .switch("switch1", "a", "b",
        node.Case("c1",
          GraphBuilder
            .enricher("enricher12", "a", "")
            .sink("end1", "")
        ),
        node.Case("c2",
          GraphBuilder
            .enricher("enricher22", "a", "")
            .sink("end2", "")
        )
      )
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), ProcessingType.Streaming)
    val counter = new ProcessCountsReporter()

    counter.reportCounts(displayable, counts) shouldBe Map(
      "source" -> 5,
      "filter1" -> 5,
      "enricher1" -> 3,
      "switch1" -> 3,
      "enricher12" -> 1,
      "enricher22" -> 2,
      "end1" -> 1,
      "end2" -> 2
    )
  }

  it should "count events at every node for process4" in {
    val counts = ProcessBaseCounts(16, Map("end11" -> 1, "end31" -> 0, "end32" -> 1, "end33" -> 5),
      Map("filter31" -> 4, "filter32" -> 0, "filter33" -> 3, "filter21" -> 2), Map.empty)
    val process = EspProcessBuilder
      .id("fooProcess")
      .exceptionHandler()
      .source("source", "start")
      .enricher("enricher1", "a", "")
      .filter("filter1", "",
        GraphBuilder.processor("proc11", "")
          .sink("end11", "")
      )
      .filter("filter21", "")
      .switch("switch21", "a", "b",
        node.Case("c1",
          GraphBuilder
            .filter("filter31", "")
            .sink("end31", "")
        ),
        node.Case("c2",
          GraphBuilder
            .filter("filter32", "")
            .sink("end32", "")
        ),
        node.Case("c3",
          GraphBuilder
            .filter("filter33", "")
            .sink("end33", "")
        )
      )
    val displayable = ProcessConverter.toDisplayable(ProcessCanonizer.canonize(process), ProcessingType.Streaming)
    val counter = new ProcessCountsReporter()

    counter.reportCounts(displayable, counts) shouldBe Map(
      "source" -> 16,
      "enricher1" -> 16,
      "filter1" -> 16,
      "proc11" -> 1,
      "end11" -> 1,
      "filter21" -> 15,
      "switch21" -> 13,
      "filter31" -> 4,
      "end31" -> 0,
      "filter32" -> 1,
      "end32" -> 1,
      "filter33" -> 8,
      "end33" -> 5
    )
  }
}
