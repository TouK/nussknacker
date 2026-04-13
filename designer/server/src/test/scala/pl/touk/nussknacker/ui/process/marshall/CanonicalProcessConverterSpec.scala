package pl.touk.nussknacker.ui.process.marshall

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, NodeName, StreamMetaData}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, CanonicalProcessConverter}
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterFalse, FilterTrue, NextSwitch, SwitchDefault}
import pl.touk.nussknacker.engine.graph.evaluatedparam.BranchParameters
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel.SpelExtension._

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.io.{Source => IoSource}
import scala.util.Using

class CanonicalProcessConverterSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  private val metaData = StreamMetaData(Some(2), Some(false))

  private def loadCanonicalFromResource(resourcePath: String): CanonicalProcess = {
    val json = Using.resource(IoSource.fromResource(resourcePath))(_.mkString)
    ProcessMarshaller.fromJson(json).fold(err => fail(s"Failed to parse JSON: $err"), identity)
  }

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
        Processor(NodeId("e"), NodeName("e"), ServiceRef("ref", List())),
        Source(NodeId("s"), NodeName("s"), SourceRef("sourceRef", List()))
      ),
      List(Edge(NodeId("s"), NodeId("e"), None))
    )

    scenarioGraphCanonicalRoundTrip(scenarioGraph).nodes.toSet shouldBe scenarioGraph.nodes.toSet
  }

  test("convert process with branches") {
    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(
        Processor(NodeId("e"), NodeName("e"), ServiceRef("ref", List.empty)),
        Join(NodeId("j1"), NodeName("j1"), Some("out1"), "joinRef", List.empty, List(BranchParameters("s1", List()))),
        Source(NodeId("s2"), NodeName("s2"), SourceRef("sourceRef", List.empty)),
        Source(NodeId("s1"), NodeName("s1"), SourceRef("sourceRef", List.empty))
      ),
      List(
        Edge(NodeId("s1"), NodeId("j1"), None),
        Edge(NodeId("s2"), NodeId("j1"), None),
        Edge(NodeId("j1"), NodeId("e"), None)
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
      Edge(NodeId("sourceId1"), NodeId("join1"), None),
      Edge(NodeId("sourceId2"), NodeId("filter2"), None),
      Edge(NodeId("filter2"), NodeId("join1"), Some(FilterTrue)),
      Edge(NodeId("join1"), NodeId("end"), None)
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

    foundNodes.map(_.id.value).toSet shouldBe Set("sourceId1", "split1", "join1", "end")
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
        Edge(NodeId("source1"), NodeId(nodeId), None),
        Edge(NodeId(nodeId), NodeId("join1"), typ),
        Edge(NodeId("join1"), NodeId("end"), None)
      ) ++ additionalEdges
    }

    testCase(_.split(nodeId, branchEnd))
    testCase(
      _.filter(nodeId, "false".spel, branchEnd).emptySink("end2", "out1"),
      Some(FilterFalse),
      Set(Edge(NodeId(nodeId), NodeId("end2"), Some(FilterTrue)))
    )
    testCase(_.switch(nodeId, "false".spel, "out1", Case("1".spel, branchEnd)), Some(NextSwitch("1".spel)))
    testCase(
      _.switch(nodeId, "false".spel, "out1", branchEnd, Case("1".spel, GraphBuilder.emptySink("end2", "out1"))),
      Some(SwitchDefault),
      Set(Edge(NodeId(nodeId), NodeId("end2"), Some(NextSwitch("1".spel))))
    )
  }

  test("handle legacy edge ids pointing to node names (e.g. Union) when node ids are UUIDs") {
    def uuidOf(s: String): String = UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8)).toString

    val sourceUuid = uuidOf("source1")
    val unionUuid  = uuidOf("Union")
    val sinkUuid   = uuidOf("sink1")

    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(
        Source(NodeId(sourceUuid), NodeName("source1"), SourceRef("sourceRef", List.empty)),
        Join(
          NodeId(unionUuid),
          NodeName("Union"),
          Some("out"),
          "union",
          List.empty,
          List(BranchParameters("source1", List.empty))
        ),
        Sink(NodeId(sinkUuid), NodeName("sink1"), SinkRef("kafka", List.empty))
      ),
      List(
        // Legacy-style references by node name instead of UUID.
        Edge(NodeId(sourceUuid), NodeId("Union"), None),
        Edge(NodeId("Union"), NodeId(sinkUuid), None)
      )
    )

    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, ProcessName("legacy-union"))

    canonical.collectAllNodes.collect { case b: BranchEndData => b.definition } should contain only
      BranchEndDefinition("source1", unionUuid)
  }

  test("normalize branch ids from UUID node ids to node names for joins") {
    def uuidOf(s: String): String = UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8)).toString

    val sourceUuid = uuidOf("source1")
    val unionUuid  = uuidOf("Union")
    val sinkUuid   = uuidOf("sink1")

    val scenarioGraph = ScenarioGraph(
      ProcessProperties(metaData),
      List(
        Source(NodeId(sourceUuid), NodeName("source1"), SourceRef("sourceRef", List.empty)),
        Join(
          NodeId(unionUuid),
          NodeName("Union"),
          Some("out"),
          "union",
          List.empty,
          List(BranchParameters(sourceUuid, List.empty))
        ),
        Sink(NodeId(sinkUuid), NodeName("sink1"), SinkRef("kafka", List.empty))
      ),
      List(
        Edge(NodeId(sourceUuid), NodeId(unionUuid), None),
        Edge(NodeId(unionUuid), NodeId(sinkUuid), None)
      )
    )

    val canonical = CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, ProcessName("uuid-branch-id-union"))

    val joinNode = canonical.collectAllNodes
      .collectFirst { case j: Join => j }
      .getOrElse(fail("Missing Join node after conversion"))
    joinNode.branchParameters.map(_.branchId) should contain only "source1"

    canonical.collectAllNodes.collect { case b: BranchEndData => b.definition } should contain only
      BranchEndDefinition("source1", unionUuid)
  }

  test("handle large union scenario fixture with legacy name-based edge references") {
    val canonicalProcess = loadCanonicalFromResource("process/marshall/scenario-with-union.json")
    val scenarioGraph    = canonicalProcess.toScenarioGraph

    val unionId = scenarioGraph.nodes
      .collectFirst { case node if node.name.value == "Union" => node.id }
      .getOrElse(fail("Missing Union node in fixture"))
    val splitId = scenarioGraph.nodes
      .collectFirst { case node if node.name.value == "Split" => node.id }
      .getOrElse(fail("Missing Split node in fixture"))

    val legacyEdges = scenarioGraph.edges.map { edge =>
      val maybeLegacyFrom = if (edge.from == splitId) NodeId("Split") else edge.from
      val maybeLegacyTo   = if (edge.to == unionId) NodeId("Union") else edge.to
      edge.copy(from = maybeLegacyFrom, to = maybeLegacyTo)
    }

    val legacyScenarioGraph = scenarioGraph.copy(edges = legacyEdges)
    val converted = CanonicalProcessConverter.fromScenarioGraph(legacyScenarioGraph, ProcessName("legacy-union-large"))

    val unionNode = converted.collectAllNodes
      .collectFirst { case j: Join if j.name.value == "Union" => j }
      .getOrElse(fail("Missing Union node after conversion"))
    unionNode.branchParameters.map(_.branchId) should contain("Split")

    val unionBranchEnd = converted.collectAllNodes
      .collectFirst {
        case b: BranchEndData if b.definition.id == "Split" => b
      }
      .getOrElse(fail("Missing BranchEndData for Split branch after conversion"))

    unionBranchEnd.definition.joinId shouldBe unionId.value
  }

}
