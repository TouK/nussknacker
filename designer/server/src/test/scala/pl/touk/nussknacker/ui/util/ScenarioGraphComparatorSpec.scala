package pl.touk.nussknacker.ui.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{LayoutData, NodeId, NodeName, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterTrue, NextSwitch}
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node.{
  Case,
  CustomNode,
  Dimensions,
  Enricher,
  Filter,
  FragmentInput,
  FragmentInputDefinition,
  FragmentOutputDefinition,
  FragmentUsageOutput,
  Join,
  NodeData,
  Processor,
  Sink,
  Source,
  Split,
  StickyNote,
  Switch,
  UserDefinedAdditionalNodeFields,
  Variable,
  VariableBuilder
}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator._

class ScenarioGraphComparatorSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("detect not existing node in other process") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.emptySink("end", "testSink"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeNotPresentInOther(
        "filter1",
        Filter(NodeId("filter1"), NodeName("filter1"), "#input == 4".spel)
      ),
      "Edge from 'start' to 'filter1'" -> EdgeNotPresentInOther(
        "start",
        "filter1",
        Edge(NodeId("start"), NodeId("filter1"), None)
      ),
      "Edge from 'filter1' to 'end'" -> EdgeNotPresentInOther(
        "filter1",
        "end",
        Edge(NodeId("filter1"), NodeId("end"), Some(FilterTrue))
      ),
      "Edge from 'start' to 'end'" -> EdgeNotPresentInCurrent(
        "start",
        "end",
        Edge(NodeId("start"), NodeId("end"), None)
      )
    )
  }

  test("detect not existing node in current process") {
    val current = toDisplayable(_.emptySink("end", "testSink"))
    val other   = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeNotPresentInCurrent(
        "filter1",
        Filter(NodeId("filter1"), NodeName("filter1"), "#input == 4".spel)
      ),
      "Edge from 'start' to 'filter1'" -> EdgeNotPresentInCurrent(
        "start",
        "filter1",
        Edge(NodeId("start"), NodeId("filter1"), None)
      ),
      "Edge from 'filter1' to 'end'" -> EdgeNotPresentInCurrent(
        "filter1",
        "end",
        Edge(NodeId("filter1"), NodeId("end"), Some(FilterTrue))
      ),
      "Edge from 'start' to 'end'" -> EdgeNotPresentInOther(
        "start",
        "end",
        Edge(NodeId("start"), NodeId("end"), None)
      )
    )
  }

  test("detect changed nodes") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.filter("filter1", "#input == 8".spel).emptySink("end", "testSink"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeDifferent(
        "filter1",
        Filter(NodeId("filter1"), NodeName("filter1"), "#input == 4".spel),
        Filter(NodeId("filter1"), NodeName("filter1"), "#input == 8".spel)
      )
    )
  }

  test("detect changed edges") {
    val current = toDisplayable(_.switch("switch1", "#input".spel, "var", caseWithExpression("current")))
    val other   = toDisplayable(_.switch("switch1", "#input".spel, "var", caseWithExpression("other")))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Edge from 'switch1' to 'end1'" -> EdgeDifferent(
        "switch1",
        "end1",
        Edge(NodeId("switch1"), NodeId("end1"), Some(NextSwitch("current".spel))),
        Edge(NodeId("switch1"), NodeId("end1"), Some(NextSwitch("other".spel)))
      )
    )
  }

  test("describeMeaningfulDiffs returns empty for identical graphs") {
    val current = toDisplayable(_.emptySink("end", "testSink"))
    describeDiffsOf(current, current) shouldBe empty
  }

  test("describeMeaningfulDiffs describes a node content change") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.filter("filter1", "#input == 8".spel).emptySink("end", "testSink"))
    describeDiffsOf(current, other) shouldBe List("Node 'filter1' modified")
  }

  test("describeMeaningfulDiffs is empty when a node only moved") {
    val current = graphWithNode(filterWithLayout("#input == 4", LayoutData(10, 20)))
    val other   = graphWithNode(filterWithLayout("#input == 4", LayoutData(99, 99)))
    describeDiffsOf(current, other) shouldBe empty
  }

  test("describeMeaningfulDiffs describes the change when a node both moved and changed") {
    val current = graphWithNode(filterWithLayout("#input == 4", LayoutData(10, 20)))
    val other   = graphWithNode(filterWithLayout("#input == 8", LayoutData(99, 99)))
    describeDiffsOf(current, other) shouldBe List("Node 'filter1' modified")
  }

  test("describeMeaningfulDiffs ignores a layout-only change for every node type that carries layout data") {
    val nodesWithLayout = Table(
      ("nodeAtA", "nodeAtB"),
      (
        Source(nodeId, nodeName, SourceRef("t", Nil), layoutAt(1, 1)),
        Source(nodeId, nodeName, SourceRef("t", Nil), layoutAt(9, 9))
      ),
      (
        Filter(nodeId, nodeName, "#input".spel, None, layoutAt(1, 1)),
        Filter(nodeId, nodeName, "#input".spel, None, layoutAt(9, 9))
      ),
      (
        Switch(nodeId, nodeName, None, None, layoutAt(1, 1)),
        Switch(nodeId, nodeName, None, None, layoutAt(9, 9))
      ),
      (
        Variable(nodeId, nodeName, "v", "1".spel, layoutAt(1, 1)),
        Variable(nodeId, nodeName, "v", "1".spel, layoutAt(9, 9))
      ),
      (
        VariableBuilder(nodeId, nodeName, "v", Nil, layoutAt(1, 1)),
        VariableBuilder(nodeId, nodeName, "v", Nil, layoutAt(9, 9))
      ),
      (Split(nodeId, nodeName, layoutAt(1, 1)), Split(nodeId, nodeName, layoutAt(9, 9))),
      (
        Sink(nodeId, nodeName, SinkRef("t", Nil), None, None, layoutAt(1, 1)),
        Sink(nodeId, nodeName, SinkRef("t", Nil), None, None, layoutAt(9, 9))
      ),
      (
        CustomNode(nodeId, nodeName, None, "t", Nil, layoutAt(1, 1)),
        CustomNode(nodeId, nodeName, None, "t", Nil, layoutAt(9, 9))
      ),
      (
        Join(nodeId, nodeName, None, "t", Nil, Nil, layoutAt(1, 1)),
        Join(nodeId, nodeName, None, "t", Nil, Nil, layoutAt(9, 9))
      ),
      (
        Enricher(nodeId, nodeName, ServiceRef("s", Nil), "out", layoutAt(1, 1)),
        Enricher(nodeId, nodeName, ServiceRef("s", Nil), "out", layoutAt(9, 9))
      ),
      (
        Processor(nodeId, nodeName, ServiceRef("s", Nil), None, layoutAt(1, 1)),
        Processor(nodeId, nodeName, ServiceRef("s", Nil), None, layoutAt(9, 9))
      ),
      (
        FragmentInput(nodeId, nodeName, FragmentRef("f", Nil), layoutAt(1, 1)),
        FragmentInput(nodeId, nodeName, FragmentRef("f", Nil), layoutAt(9, 9))
      ),
      (
        FragmentUsageOutput(nodeId, nodeName, NodeId("start"), "out", None, layoutAt(1, 1)),
        FragmentUsageOutput(nodeId, nodeName, NodeId("start"), "out", None, layoutAt(9, 9))
      ),
      (
        FragmentInputDefinition(nodeId, nodeName, Nil, layoutAt(1, 1)),
        FragmentInputDefinition(nodeId, nodeName, Nil, layoutAt(9, 9))
      ),
      (
        FragmentOutputDefinition(nodeId, nodeName, "out", Nil, layoutAt(1, 1)),
        FragmentOutputDefinition(nodeId, nodeName, "out", Nil, layoutAt(9, 9))
      ),
    )

    forAll(nodesWithLayout) { (nodeAtA: NodeData, nodeAtB: NodeData) =>
      describeDiffsOf(graphWithNode(nodeAtA), graphWithNode(nodeAtB)) shouldBe empty
    }
  }

  test("describeMeaningfulDiffs treats the different spellings of empty additional fields as the same") {
    val emptySpellings = Table(
      ("fieldsA", "fieldsB"),
      (None, Some(UserDefinedAdditionalNodeFields(description = None, layoutData = None))),
      (None, layoutAt(9, 9)),
      (None, Some(UserDefinedAdditionalNodeFields(description = Some(""), layoutData = None))),
      (
        Some(UserDefinedAdditionalNodeFields(description = Some(""), layoutData = Some(LayoutData(1, 1)))),
        Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(9, 9))))
      ),
    )

    forAll(emptySpellings) {
      (fieldsA: Option[UserDefinedAdditionalNodeFields], fieldsB: Option[UserDefinedAdditionalNodeFields]) =>
        val nodeA = Filter(nodeId, nodeName, "#input".spel, None, fieldsA)
        val nodeB = Filter(nodeId, nodeName, "#input".spel, None, fieldsB)
        describeDiffsOf(graphWithNode(nodeA), graphWithNode(nodeB)) shouldBe empty
        describeDiffsOf(graphWithNode(nodeB), graphWithNode(nodeA)) shouldBe empty
    }
  }

  test("describeMeaningfulDiffs still reports a description that was actually set") {
    val withoutDescription = Filter(nodeId, nodeName, "#input".spel, None, layoutAt(1, 1))
    val withDescription = Filter(
      nodeId,
      nodeName,
      "#input".spel,
      None,
      Some(UserDefinedAdditionalNodeFields(description = Some("why"), layoutData = Some(LayoutData(9, 9))))
    )
    describeDiffsOf(graphWithNode(withoutDescription), graphWithNode(withDescription)) shouldBe List(
      "Node 'n' modified"
    )
  }

  // more than four entries on purpose - up to four, scala's Map preserves insertion order by itself
  test("describeMeaningfulDiffs describes added and removed nodes and edges in a stable order") {
    val current = toDisplayable(
      _.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"),
      description = Some("current")
    )
    val other = toDisplayable(_.emptySink("end", "testSink"), description = Some("other"))
    describeDiffsOf(current, other) shouldBe List(
      "Properties modified",
      "Node 'filter1' added",
      "Edge 'filter1' → 'end' added",
      "Edge 'start' → 'end' removed",
      "Edge 'start' → 'filter1' added"
    )
  }

  // "added" and "removed" are from the current graph's point of view, and swapping them is the kind of
  // mistake that reads fine in isolation.
  test("describeMeaningfulDiffs describes removals from the other direction") {
    val current = toDisplayable(_.emptySink("end", "testSink"))
    val other   = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    describeDiffsOf(current, other) should contain("Node 'filter1' removed")
  }

  test("describeMeaningfulDiffs describes an added and a removed sticky note") {
    val note    = StickyNote("note1", "content", "#fff", Dimensions(200, 100), layoutAt(1, 1))
    val without = ScenarioGraph(processProperties(), nodes = Nil, edges = Nil)

    describeDiffsOf(graphWithStickyNote(note), without) shouldBe List("Note 'note1' added")
    describeDiffsOf(without, graphWithStickyNote(note)) shouldBe List("Note 'note1' removed")
  }

  // The count is what the client reports as "…and N more", so it has to be the real total rather than the
  // length of the truncated list.
  test("describeMeaningfulDiffs counts every change but describes only up to the limit") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.emptySink("end", "testSink"), description = Some("other"))

    val total = changeCountOf(current, other)
    val (described, reported) = ScenarioGraphComparator.describeMeaningfulDiffs(
      ScenarioGraphComparator.compare(current, other),
      2
    )

    total should be > 2
    reported shouldBe total
    described shouldBe describeDiffsOf(current, other).take(2)
  }

  test("describeMeaningfulDiffs describes an edge change") {
    val current = toDisplayable(_.switch("switch1", "#input".spel, "var", caseWithExpression("current")))
    val other   = toDisplayable(_.switch("switch1", "#input".spel, "var", caseWithExpression("other")))
    describeDiffsOf(current, other) shouldBe List("Edge 'switch1' → 'end1' modified")
  }

  test("describeMeaningfulDiffs is empty when a sticky note only moved") {
    val current = graphWithStickyNote(StickyNote("note1", "content", "#fff", Dimensions(200, 100), layoutAt(1, 1)))
    val other   = graphWithStickyNote(StickyNote("note1", "content", "#fff", Dimensions(200, 100), layoutAt(9, 9)))
    describeDiffsOf(current, other) shouldBe empty
  }

  test("describeMeaningfulDiffs describes a sticky note content change") {
    val current = graphWithStickyNote(StickyNote("note1", "content A", "#fff", Dimensions(200, 100), layoutAt(1, 1)))
    val other   = graphWithStickyNote(StickyNote("note1", "content B", "#fff", Dimensions(200, 100), layoutAt(9, 9)))
    describeDiffsOf(current, other) shouldBe List("Note 'note1' modified")
  }

  // `dimensions` sits outside `additionalFields`, where the stripping happens - pinned as it behaves
  test("describeMeaningfulDiffs reports a sticky note that was only resized") {
    val current = graphWithStickyNote(StickyNote("note1", "content", "#fff", Dimensions(200, 100), layoutAt(1, 1)))
    val other   = graphWithStickyNote(StickyNote("note1", "content", "#fff", Dimensions(400, 100), layoutAt(1, 1)))
    describeDiffsOf(current, other) shouldBe List("Note 'note1' modified")
  }

  test("describeMeaningfulDiffs describes a properties change") {
    val current = toDisplayable(_.emptySink("end", "testSink"), description = Some("current"))
    val other   = toDisplayable(_.emptySink("end", "testSink"), description = Some("other"))
    describeDiffsOf(current, other) shouldBe List("Properties modified")
  }

  test("detect changed description") {
    val current = toDisplayable(_.emptySink("end", "testSink"), description = Some("current"))
    val other   = toDisplayable(_.emptySink("end", "testSink"), description = Some("other"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Properties" -> PropertiesDifferent(
        processProperties(description = Some("current")),
        processProperties(description = Some("other"))
      )
    )
  }

  test("detect changed property") {
    val current = toDisplayable(_.emptySink("end", "testSink"), properties = Map("key" -> "current"))
    val other   = toDisplayable(_.emptySink("end", "testSink"), properties = Map("key" -> "other"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Properties" -> PropertiesDifferent(
        processProperties(properties = Map("key" -> "current")),
        processProperties(properties = Map("key" -> "other"))
      )
    )
  }

  private def describeDiffsOf(current: ScenarioGraph, other: ScenarioGraph): List[String] =
    ScenarioGraphComparator.describeMeaningfulDiffs(ScenarioGraphComparator.compare(current, other), Int.MaxValue)._1

  private def changeCountOf(current: ScenarioGraph, other: ScenarioGraph): Int =
    ScenarioGraphComparator.describeMeaningfulDiffs(ScenarioGraphComparator.compare(current, other), Int.MaxValue)._2

  private def layoutAt(x: Long, y: Long): Option[UserDefinedAdditionalNodeFields] =
    Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(x, y))))

  private val nodeId   = NodeId("n")
  private val nodeName = NodeName("n")

  private def filterWithLayout(expression: String, layout: LayoutData): Filter =
    Filter(
      NodeId("filter1"),
      NodeName("filter1"),
      expression.spel,
      additionalFields = layoutAt(layout.x, layout.y)
    )

  private def graphWithNode(node: NodeData): ScenarioGraph =
    ScenarioGraph(processProperties(), nodes = List(node), edges = Nil)

  private def graphWithStickyNote(stickyNote: StickyNote): ScenarioGraph =
    ScenarioGraph(processProperties(), nodes = Nil, edges = Nil, stickyNotes = List(stickyNote))

  private def toDisplayable(
      scenario: GraphBuilder[CanonicalProcess] => CanonicalProcess,
      description: Option[String] = None,
      properties: Map[String, String] = Map.empty
  ): ScenarioGraph =
    scenario(
      ScenarioBuilder
        .streaming("test")
        .additionalFields(
          description = description,
          properties = properties
        )
        .parallelism(1)
        .source("start", "testSource")
    ).toScenarioGraph

  private def caseWithExpression(expr: String, id: Int = 1): Case = {
    Case(expr.spel, GraphBuilder.emptySink(s"end$id", "end"))
  }

  private def processProperties(
      description: Option[String] = None,
      properties: Map[String, String] = Map.empty
  ): ProcessProperties = {
    ProcessProperties.combineTypeSpecificProperties(
      typeSpecificProperties = StreamMetaData(
        parallelism = Some(1)
      ),
      additionalFields = ProcessAdditionalFields(
        description,
        properties,
        StreamMetaData.typeName
      )
    )
  }

}
