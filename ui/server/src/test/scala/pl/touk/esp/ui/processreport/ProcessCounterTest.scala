package pl.touk.esp.ui.processreport

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.{MetaData, StreamMetaData}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.graph.node.{Filter, SubprocessOutputDefinition}
import pl.touk.esp.engine.spel
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{Group, ProcessAdditionalFields}
import pl.touk.esp.ui.process.subprocess.SubprocessRepository

//numbers & processes in this test can be totaly uncorrect and unrealistic, as processCounter does not care
//about actual values, only assigns them to nodes
class ProcessCounterTest extends FlatSpec with Matchers {

  import spel.Implicits._

  it should "compute counts for simple process" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder
      .id("test").parallelism(1).exceptionHandler()
      .source("source1", "")
      .filter("filter1", "")
      .sink("sink11", ""))
    val counter = new ProcessCounter(subprocessRepository(Set()))

    val computed = counter.computeCounts(process, Map("source1" -> RawCount(30L, 5L),
      "filter1" -> RawCount(20, 10)))

    computed shouldBe Map(
      "source1" -> NodeCount(30, 5),
      "filter1" -> NodeCount(20, 10),
      "sink11" -> NodeCount(0, 0)
    )
  }

  it should "compute counts with groups" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder
      .id("test").parallelism(1).exceptionHandler()
      .source("source1", "")
      .filter("filter1", "")
      .sink("sink11", "")).copy(metaData = MetaData("test", StreamMetaData(), false,
        Some(ProcessAdditionalFields(Some(""), Set(Group("gr1", Set("filter1", "sink11")))))))
    val processCounter = new ProcessCounter(subprocessRepository(Set()))

    val computed = processCounter.computeCounts(process, Map("source1" -> RawCount(50, 0L),
      "filter1" -> RawCount(40, 9), "sink11" -> RawCount(30, 8)))

    computed shouldBe Map(
      "source1" -> NodeCount(50, 0),
      "filter1" -> NodeCount(40, 9),
      "sink11" -> NodeCount(30, 8),
      "gr1" -> NodeCount(40, 9)
    )
  }

  it should "compute counts for subprocess" in {
    val process = ProcessCanonizer.canonize(EspProcessBuilder
      .id("test").parallelism(1).exceptionHandler()
      .source("source1", "")
      .filter("filter1", "")
      .subprocessOneOut("sub1", "subprocess1", "out1")
      .sink("sink11", ""))


    val counter = new ProcessCounter(subprocessRepository(Set(
      CanonicalProcess(MetaData("subprocess1", null), null,
          List(FlatNode(Filter("subFilter1", "")),
            FlatNode(Filter("subFilter2", "")), FlatNode(SubprocessOutputDefinition("outId1", "out1")))
        )
    )))

    val computed = counter.computeCounts(process, Map("source1" -> RawCount(70L, 0L),
      "filter1" -> RawCount(60, 1),
      "sub1" -> RawCount(55, 2),
      "sub1-subFilter1" -> RawCount(45, 4),
      "sub1-outId1" -> RawCount(35, 5),
      "sink11" -> RawCount(30, 10)))

    computed shouldBe Map(
      "source1" -> NodeCount(70, 0),
      "filter1" -> NodeCount(60, 1),
      "sub1" -> NodeCount(55, 2
        , Map("subFilter1" -> NodeCount(45, 4),
        "subFilter2" -> NodeCount(0, 0),
        "outId1" -> NodeCount(35, 5)
      )),
      "sink11" -> NodeCount(30, 10)
    )
  }


  private def subprocessRepository(processes: Set[CanonicalProcess]) = {
    new SubprocessRepository {
      override def loadSubprocesses(): Set[CanonicalProcess] = processes
    }
  }
}
