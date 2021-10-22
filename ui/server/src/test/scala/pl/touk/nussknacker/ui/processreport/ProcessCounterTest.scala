package pl.touk.nussknacker.ui.processreport

import cats.data.NonEmptyList
import org.scalatest.{FlatSpec, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{Filter, SubprocessInputDefinition, SubprocessOutputDefinition}
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.ui.api.helpers.ProcessTestData.SetSubprocessRepository
import pl.touk.nussknacker.ui.process.subprocess.{SubprocessDetails, SubprocessRepository}

//numbers & processes in this test can be totaly uncorrect and unrealistic, as processCounter does not care
//about actual values, only assigns them to nodes
class ProcessCounterTest extends FunSuite with Matchers {

  import spel.Implicits._

  private val defaultCounter = new ProcessCounter(new SetSubprocessRepository(Set()))

  test("compute counts for simple process") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder
      .id("test").parallelism(1).exceptionHandler()
      .source("source1", "")
      .filter("filter1", "")
      .emptySink("sink11", ""))

    val computed = defaultCounter.computeCounts(process, Map("source1" -> RawCount(30L, 5L),
      "filter1" -> RawCount(20, 10)).get)

    computed shouldBe Map(
      "source1" -> NodeCount(30, 5),
      "filter1" -> NodeCount(20, 10),
      "sink11" -> NodeCount(0, 0)
    )
  }

  test("compute counts for branches") {
    val process = EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
        GraphBuilder
          .source("source1", "source")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("source2", "source")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .branch("join1", "union", None,
            List(
              "branch1" -> List(),
              "branch2" -> List()
            )
          )
          .emptySink("end", "sink")
      ))
    val result = defaultCounter.computeCounts(ProcessCanonizer.canonize(process), Map(
      "source1" -> RawCount(1, 0),
      "source2" -> RawCount(2, 0),
      "join1" -> RawCount(3, 0),
      "end" -> RawCount(4, 0)
    ).get)

    result shouldBe Map(
      "source1" -> NodeCount(1, 0),
      "source2" -> NodeCount(2, 0),
      "join1" -> NodeCount(3, 0),
      "end" -> NodeCount(4, 0)
    )
  }

  test("compute counts for fragment") {
    val process = ProcessCanonizer.canonize(EspProcessBuilder
      .id("test").parallelism(1).exceptionHandler()
      .source("source1", "")
      .filter("filter1", "")
      .subprocessOneOut("sub1", "subprocess1", "out1")
      .emptySink("sink11", ""))


    val counter = new ProcessCounter(subprocessRepository(Set(
      CanonicalProcess(MetaData("subprocess1", FragmentSpecificData()), null,
          List(
            FlatNode(SubprocessInputDefinition("subInput1", List())),
            FlatNode(Filter("subFilter1", "")),
            FlatNode(Filter("subFilter2", "")),
            FlatNode(SubprocessOutputDefinition("outId1", "out1", List.empty))), List.empty
        )
    )))

    val computed = counter.computeCounts(process, Map("source1" -> RawCount(70L, 0L),
      "filter1" -> RawCount(60, 1),
      "sub1" -> RawCount(55, 2),
      "sub1-subFilter1" -> RawCount(45, 4),
      "sub1-outId1" -> RawCount(35, 5),
      "sink11" -> RawCount(30, 10)).get)

    computed shouldBe Map(
      "source1" -> NodeCount(70, 0),
      "filter1" -> NodeCount(60, 1),
      "sub1" -> NodeCount(55, 2
        , Map(
          "subInput1" -> NodeCount(55, 2),
          "subFilter1" -> NodeCount(45, 4),
          "subFilter2" -> NodeCount(0, 0),
          "outId1" -> NodeCount(35, 5)
      )),
      "sink11" -> NodeCount(30, 10)
    )
  }


  private def subprocessRepository(processes: Set[CanonicalProcess]): SubprocessRepository = {
    new SubprocessRepository {
      override def loadSubprocesses(versions: Map[String, Long]): Set[SubprocessDetails] = {
        processes.map(c => SubprocessDetails(c, "category1"))
      }
    }
  }
}
