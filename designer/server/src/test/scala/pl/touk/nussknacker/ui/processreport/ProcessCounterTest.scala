package pl.touk.nussknacker.ui.processreport

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{FragmentSpecificData, MetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.node.{Filter, FragmentInputDefinition, FragmentOutputDefinition}
import pl.touk.nussknacker.test.mock.StubFragmentRepository
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.security.api.{AdminUser, LoggedUser}

//numbers & processes in this test can be totally incorrect and unrealistic, as processCounter does not care
//about actual values, only assigns them to nodes
class ProcessCounterTest extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val defaultCounter = new ProcessCounter(new StubFragmentRepository(Map.empty))

  private implicit val user: LoggedUser = AdminUser("admin", "admin")

  test("compute counts for simple process") {
    val process = ScenarioBuilder
      .streaming("test")
      .parallelism(1)
      .source("source1", "")
      .filter("filter1", "".spel)
      .emptySink("sink11", "")

    val computed =
      defaultCounter.computeCounts(
        process,
        isFragment = false,
        Map("source1" -> RawCount(30L, 5L), "filter1" -> RawCount(20, 10)).get
      )

    computed shouldBe Map(
      "source1" -> NodeCount(30, 5),
      "filter1" -> NodeCount(20, 10),
      "sink11"  -> NodeCount(0, 0)
    )
  }

  test("compute counts for branches") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("source1", "source")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("source2", "source")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join(
            "join1",
            "union",
            None,
            List(
              "branch1" -> List(),
              "branch2" -> List()
            )
          )
          .emptySink("end", "sink")
      )
    val result = defaultCounter.computeCounts(
      process,
      isFragment = false,
      Map(
        "source1" -> RawCount(1, 0),
        "source2" -> RawCount(2, 0),
        "join1"   -> RawCount(3, 0),
        "end"     -> RawCount(4, 0)
      ).get
    )

    result shouldBe Map(
      "source1" -> NodeCount(1, 0),
      "source2" -> NodeCount(2, 0),
      "join1"   -> NodeCount(3, 0),
      "end"     -> NodeCount(4, 0)
    )
  }

  test("compute counts for scenario with fragment") {
    val process = ScenarioBuilder
      .streaming("test")
      .parallelism(1)
      .source("source1", "")
      .filter("filter1", "".spel)
      .fragmentOneOut("fragment1", "fragment1", "out1", "fragmentResult")
      .emptySink("sink11", "")

    val counter = new ProcessCounter(
      fragmentRepository(
        List(
          CanonicalProcess(
            MetaData("fragment1", FragmentSpecificData()),
            List(
              FlatNode(FragmentInputDefinition("subInput1", List())),
              FlatNode(Filter("subFilter1", "".spel)),
              FlatNode(Filter("subFilter2", "".spel)),
              FlatNode(FragmentOutputDefinition("outId1", "out1", List.empty))
            ),
            List.empty
          )
        )
      )
    )

    val computed = counter.computeCounts(
      process,
      isFragment = false,
      Map(
        "source1"              -> RawCount(70L, 0L),
        "filter1"              -> RawCount(60, 1),
        "fragment1"            -> RawCount(55, 2),
        "fragment1-subFilter1" -> RawCount(45, 4),
        "fragment1-outId1"     -> RawCount(35, 5),
        "sink11"               -> RawCount(30, 10)
      ).get
    )

    computed shouldBe Map(
      "source1" -> NodeCount(70, 0),
      "filter1" -> NodeCount(60, 1),
      "fragment1" -> NodeCount(
        55,
        2,
        Map(
          "subInput1"  -> NodeCount(55, 2),
          "subFilter1" -> NodeCount(45, 4),
          "subFilter2" -> NodeCount(0, 0),
          "outId1"     -> NodeCount(35, 5)
        )
      ),
      "sink11" -> NodeCount(30, 10)
    )
  }

  test("compute counts for fragment") {
    val fragment = ScenarioBuilder
      .fragment("fragment1", "in" -> classOf[String])
      .filter("filter", "#in != 'stop'".spel)
      .fragmentOutput("fragmentEnd", "output", "out" -> "#in".spel)

    val counter = new ProcessCounter(fragmentRepository(List.empty))

    val computed = counter.computeCounts(
      fragment,
      isFragment = true,
      Map(
        "fragment1"   -> RawCount(30, 0),
        "filter"      -> RawCount(20, 5),
        "fragmentEnd" -> RawCount(15, 10)
      ).get
    )

    computed shouldBe Map(
      "fragment1"   -> NodeCount(30, 0),
      "filter"      -> NodeCount(20, 5),
      "fragmentEnd" -> NodeCount(15, 10)
    )
  }

  private def fragmentRepository(processes: List[CanonicalProcess]): FragmentRepository =
    new StubFragmentRepository(
      Map("not-important-processing-type" -> processes)
    )

}
