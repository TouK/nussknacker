package pl.touk.nussknacker.ui.process.marshall

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{canonicalnode, CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{CustomNodeOutput, FilterFalse, FilterTrue, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExtension._

class CanonicalProcessConverterSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  private val metaData = StreamMetaData(Some(2), Some(false))

  def canonicalDisplayableRoundTrip(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val scenarioGraph = canonicalProcess.toScenarioGraph
    CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, canonicalProcess.name)
  }

  def scenarioGraphCanonicalRoundTrip(scenarioGraph: ScenarioGraph): ScenarioGraph = {
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, ProcessName("not-used-name"))
    canonical.toScenarioGraph
  }

  test("be able to convert empty process") {
    val emptyProcess = CanonicalProcess(MetaData(id = "t1", StreamMetaData()), List(), List.empty)

    canonicalDisplayableRoundTrip(emptyProcess) shouldBe emptyProcess
  }

  test("be able to handle different node order") {
    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(
        Processor("e", ServiceRef("ref", List())),
        Source("s", SourceRef("sourceRef", List()))
      ),
      List(Edge("s", "e", None))
    )

    scenarioGraphCanonicalRoundTrip(scenarioGraph).nodes.toSet shouldBe scenarioGraph.nodes.toSet
  }

  test("convert process with branches") {
    val scenarioGraph = ScenarioGraph(
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

    scenarioGraphCanonicalRoundTrip(scenarioGraph).nodes.sortBy(_.id) shouldBe scenarioGraph.nodes.sortBy(_.id)
    scenarioGraphCanonicalRoundTrip(scenarioGraph).edges.toSet shouldBe scenarioGraph.edges.toSet

    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, name)

    canonical shouldBe processViaBuilder
  }

  test("Convert branches to scenarioGraph") {
    import pl.touk.nussknacker.engine.spel.SpelExtension._

    val process = ScenarioBuilder
      .streamingLite("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "sourceType1")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "sourceType1")
          .filter("filter2", "false".spel)
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join("join1", "union", Some("outPutVar"), List("branch1" -> Nil, "branch2" -> Nil))
          .emptySink("end", "outType1")
      )

    val scenarioGraph = process.toScenarioGraph

    scenarioGraph.edges.toSet shouldBe Set(
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

    val foundNodes = process.toScenarioGraph.nodes

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
      val edges = process.toScenarioGraph.edges
      edges.toSet shouldBe Set(
        Edge("source1", nodeId, None),
        Edge(nodeId, "join1", typ),
        Edge("join1", "end", None)
      ) ++ additionalEdges
    }

    testCase(_.split(nodeId, branchEnd))
    testCase(
      _.filter(nodeId, "false".spel, branchEnd).emptySink("end2", "out1"),
      Some(FilterFalse),
      Set(Edge(nodeId, "end2", Some(FilterTrue)))
    )
    testCase(_.switch(nodeId, "false".spel, "out1", Case("1".spel, branchEnd)), Some(NextSwitch("1".spel)))
    testCase(
      _.switch(nodeId, "false".spel, "out1", branchEnd, Case("1".spel, GraphBuilder.emptySink("end2", "out1"))),
      Some(SwitchDefault),
      Set(Edge(nodeId, "end2", Some(NextSwitch("1".spel))))
    )
  }

  test("convert a custom node with a named output to a canonical wrapper and back, the rejected one into a join") {
    val customNodeData = CustomNode("dedup", Some("outVar"), "dedupRef", List.empty)
    val sinkA          = Sink("sinkA", SinkRef("dead-end", List.empty))
    val sinkB          = Sink("sinkB", SinkRef("dead-end", List.empty))
    val join           = Join("join", Some("joined"), "joinRef", List.empty, List(BranchParameters("dedup", List())))

    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(Source("source", SourceRef("sourceRef", List.empty)), customNodeData, sinkA, join, sinkB),
      List(
        Edge("source", "dedup", None),
        Edge("dedup", "sinkA", Some(CustomNodeOutput("main"))),
        Edge("dedup", "join", Some(CustomNodeOutput("rejected"))),
        Edge("join", "sinkB", None)
      )
    )

    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, ProcessName("dedup-test"))

    canonical.nodes shouldBe List(
      FlatNode(Source("source", SourceRef("sourceRef", List.empty))),
      canonicalnode.CustomNodeWithOutputs(
        customNodeData,
        List(
          canonicalnode.Output("main", List(FlatNode(sinkA))),
          canonicalnode.Output("rejected", List(FlatNode(BranchEndData(BranchEndDefinition("dedup", "join")))))
        )
      )
    )
    canonical.additionalBranches shouldBe List(List(FlatNode(join), FlatNode(sinkB)))

    val roundTripped = canonical.toScenarioGraph
    roundTripped.nodes.toSet shouldBe scenarioGraph.nodes.toSet
    roundTripped.edges.toSet shouldBe scenarioGraph.edges.toSet
  }

  // `CustomNodeWithOutputs` has room for the named outputs and nothing else, so any other edge on such a node loses
  // its subtree. An unnamed edge has no slot next to named outputs - the main continuation travels under its name.
  // Only reachable through import/API, and `UIProcessValidator` rejects it on save - this pins what the conversion
  // does should one slip through.
  test("drop (not silently) another edge on a custom node that also has a named output") {
    val customNodeData = CustomNode("dedup", Some("outVar"), "dedupRef", List.empty)
    val rejectedSink   = Sink("sinkB", SinkRef("dead-end", List.empty))
    val droppedSink    = Sink("sinkDropped", SinkRef("dead-end", List.empty))

    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(Source("source", SourceRef("sourceRef", List.empty)), customNodeData, rejectedSink, droppedSink),
      List(
        Edge("source", "dedup", None),
        Edge("dedup", "sinkB", Some(CustomNodeOutput("rejected"))),
        Edge("dedup", "sinkDropped", None)
      )
    )

    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, ProcessName("dedup-dropped-edge"))

    canonical.nodes shouldBe List(
      FlatNode(Source("source", SourceRef("sourceRef", List.empty))),
      canonicalnode.CustomNodeWithOutputs(
        customNodeData,
        List(canonicalnode.Output("rejected", List(FlatNode(rejectedSink))))
      )
    )
  }

  test("keep the first output edge and drop (not silently) a duplicate edge with the same output name") {
    val customNodeData = CustomNode("dedup", Some("outVar"), "dedupRef", List.empty)
    val firstSink      = Sink("firstSink", SinkRef("dead-end", List.empty))
    val duplicateSink  = Sink("duplicateSink", SinkRef("dead-end", List.empty))

    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(Source("source", SourceRef("sourceRef", List.empty)), customNodeData, firstSink, duplicateSink),
      List(
        Edge("source", "dedup", None),
        Edge("dedup", "firstSink", Some(CustomNodeOutput("rejected"))),
        Edge("dedup", "duplicateSink", Some(CustomNodeOutput("rejected")))
      )
    )

    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, ProcessName("dedup-duplicate-output"))

    canonical.nodes shouldBe List(
      FlatNode(Source("source", SourceRef("sourceRef", List.empty))),
      canonicalnode.CustomNodeWithOutputs(
        customNodeData,
        List(canonicalnode.Output("rejected", List(FlatNode(firstSink))))
      )
    )
  }

  // A non-UI caller can hand in a branch edge on a Join; it must be dropped, not flattened inline as a continuation.
  test("drop (not silently) a named output edge on a join and convert the rest of the graph normally") {
    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(
        Processor("e", ServiceRef("ref", List.empty)),
        Join("j1", Some("out1"), "joinRef", List.empty, List(BranchParameters("s1", List()))),
        Source("s1", SourceRef("sourceRef", List.empty)),
        Sink("rejectedSink", SinkRef("dead-end", List.empty))
      ),
      List(
        Edge("s1", "j1", None),
        Edge("j1", "e", None),
        Edge("j1", "rejectedSink", Some(CustomNodeOutput("rejected")))
      )
    )

    val name      = ProcessName("join-with-named-output-edge")
    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, name)

    canonical shouldBe ScenarioBuilder
      .streaming(name.value)
      .parallelism(metaData.parallelism.get)
      .stateOnDisk(metaData.spillStateToDisk.get)
      .sources(
        GraphBuilder.join("j1", "joinRef", Some("out1"), List("s1" -> List())).processorEnd("e", "ref"),
        GraphBuilder.source("s1", "sourceRef").branchEnd("s1", "j1")
      )
  }

  test("regression: a plain custom node with only an unnamed edge stays a flat node, not a wrapper") {
    val customNodeData = CustomNode("plain", Some("outVar"), "plainRef", List.empty)
    val sink           = Sink("sink", SinkRef("dead-end", List.empty))

    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(Source("source", SourceRef("sourceRef", List.empty)), customNodeData, sink),
      List(
        Edge("source", "plain", None),
        Edge("plain", "sink", None)
      )
    )

    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, ProcessName("plain-custom-node"))

    canonical.nodes shouldBe List(
      FlatNode(Source("source", SourceRef("sourceRef", List.empty))),
      FlatNode(customNodeData),
      FlatNode(sink)
    )
  }

}
