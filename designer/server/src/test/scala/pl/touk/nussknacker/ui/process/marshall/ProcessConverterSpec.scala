package pl.touk.nussknacker.ui.process.marshall

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.api.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.Implicits._

class ProcessConverterSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  private val metaData = StreamMetaData(Some(2), Some(false))

  def canonicalDisplayableRoundTrip(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val displayable =
      ProcessConverter.toDisplayable(canonicalProcess)
    ProcessConverter.fromDisplayable(displayable, canonicalProcess.name)
  }

  def displayableCanonicalRoundTrip(process: DisplayableProcess): DisplayableProcess = {
    val canonical = ProcessConverter.fromDisplayable(process, ProcessName("not-used-name"))
    ProcessConverter.toDisplayable(canonical)
  }

  test("be able to convert empty process") {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1", StreamMetaData()), List(), List.empty)

    canonicalDisplayableRoundTrip(emptyProcess) shouldBe emptyProcess
  }

  test("be able to handle different node order") {
    val process = DisplayableProcess(
      ProcessProperties(metaData),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ),
      List(Edge("s", "e", None)),
    )

    displayableCanonicalRoundTrip(process).nodes.toSet shouldBe process.nodes.toSet
  }

  test("convert process with branches") {
    val process = DisplayableProcess(
      ProcessProperties(metaData),
      List(
        Processor("e", ServiceRef("ref", List.empty)),
        Join("j1", Some("out1"), "joinRef", List.empty, List(BranchParameters("s1", List()))),
        Source("s2", SourceRef("sourceRef", List.empty)),
        Source("s1", SourceRef("sourceRef", List.empty))
      ),
      List(
        Edge("s1", "j1", None),
        Edge("s2", "j1", None),
        Edge("j1", "e", None)
      )
    )

    val name = ProcessName("t1")
    val processViaBuilder = ScenarioBuilder
      .streaming(name.value)
      .parallelism(metaData.parallelism.get)
      .stateOnDisk(metaData.spillStateToDisk.get)
      .sources(
        GraphBuilder.join("j1", "joinRef", Some("out1"), List("s1" -> List())).processorEnd("e", "ref"),
        GraphBuilder.source("s2", "sourceRef").branchEnd("s2", "j1"),
        GraphBuilder.source("s1", "sourceRef").branchEnd("s1", "j1")
      )

    displayableCanonicalRoundTrip(process).nodes.sortBy(_.id) shouldBe process.nodes.sortBy(_.id)
    displayableCanonicalRoundTrip(process).edges.toSet shouldBe process.edges.toSet

    val canonical = ProcessConverter.fromDisplayable(process, name)

    canonical shouldBe processViaBuilder
  }

  test("Convert branches to displayable") {
    import pl.touk.nussknacker.engine.spel.Implicits._

    val process = ScenarioBuilder
      .streamingLite("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "sourceType1")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "sourceType1")
          .filter("filter2", "false")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join("join1", "union", Some("outPutVar"), List("branch1" -> Nil, "branch2" -> Nil))
          .emptySink("end", "outType1")
      )

    val displayableProcess = ProcessConverter.toDisplayable(process)

    displayableProcess.edges.toSet shouldBe Set(
      Edge("sourceId1", "join1", None),
      Edge("sourceId2", "filter2", None),
      Edge("filter2", "join1", Some(FilterTrue)),
      Edge("join1", "end", None)
    )
  }

  test("finds all nodes in diamond-shaped process") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "sourceType1")
          .split("split1", GraphBuilder.branchEnd("branch1", "join1"), GraphBuilder.branchEnd("branch2", "join1")),
        GraphBuilder
          .join("join1", "union", Some("outPutVar"), List("branch1" -> Nil, "branch2" -> Nil))
          .emptySink("end", "outType1")
      )

    val foundNodes = ProcessConverter.findNodes(process)

    foundNodes.map(_.id).toSet shouldBe Set("sourceId1", "split1", "join1", "end")
  }

  test("Handle switch/split/filter => union case") {
    val branchEnd      = GraphBuilder.branchEnd("branch1", "join1")
    val nodeId: String = "problemNode"

    def testCase(
        run: GraphBuilder[SourceNode] => SourceNode,
        typ: Option[EdgeType] = None,
        additionalEdges: Set[Edge] = Set.empty
    ) = {
      val process = ScenarioBuilder
        .streaming("proc1")
        .sources(
          run(
            GraphBuilder
              .source("source1", "sourceType1")
          ),
          GraphBuilder
            .join("join1", "union", Some("outPutVar"), List("branch1" -> Nil, "branch2" -> Nil))
            .emptySink("end", "outType1")
        )
      val edges = ProcessConverter.toDisplayable(process).edges
      edges.toSet shouldBe Set(
        Edge("source1", nodeId, None),
        Edge(nodeId, "join1", typ),
        Edge("join1", "end", None)
      ) ++ additionalEdges
    }

    testCase(_.split(nodeId, branchEnd))
    testCase(
      _.filter(nodeId, "false", branchEnd).emptySink("end2", "out1"),
      Some(FilterFalse),
      Set(Edge(nodeId, "end2", Some(FilterTrue)))
    )
    testCase(_.switch(nodeId, "false", "out1", Case("1", branchEnd)), Some(NextSwitch("1")))
    testCase(
      _.switch(nodeId, "false", "out1", branchEnd, Case("1", GraphBuilder.emptySink("end2", "out1"))),
      Some(SwitchDefault),
      Set(Edge(nodeId, "end2", Some(NextSwitch("1"))))
    )
  }

}
