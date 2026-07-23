package pl.touk.nussknacker.ui.util

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{LayoutData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.api.graph.{Edge, ProcessProperties, ScenarioGraph}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.graph.EdgeType.{FilterTrue, NextSwitch}
import pl.touk.nussknacker.engine.graph.node.{Case, Dimensions, Filter, StickyNote, UserDefinedAdditionalNodeFields}
import pl.touk.nussknacker.ui.util.ScenarioGraphComparator._

class ScenarioGraphComparatorSpec extends AnyFunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  test("detect not existing node in other process") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.emptySink("end", "testSink"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeNotPresentInOther("filter1", Filter("filter1", "#input == 4".spel)),
      "Edge from 'start' to 'filter1'" -> EdgeNotPresentInOther(
        "start",
        "filter1",
        Edge("start", "filter1", None)
      ),
      "Edge from 'filter1' to 'end'" -> EdgeNotPresentInOther(
        "filter1",
        "end",
        Edge("filter1", "end", Some(FilterTrue))
      ),
      "Edge from 'start' to 'end'" -> EdgeNotPresentInCurrent(
        "start",
        "end",
        Edge("start", "end", None)
      )
    )
  }

  test("detect not existing node in current process") {
    val current = toDisplayable(_.emptySink("end", "testSink"))
    val other   = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeNotPresentInCurrent("filter1", Filter("filter1", "#input == 4".spel)),
      "Edge from 'start' to 'filter1'" -> EdgeNotPresentInCurrent(
        "start",
        "filter1",
        Edge("start", "filter1", None)
      ),
      "Edge from 'filter1' to 'end'" -> EdgeNotPresentInCurrent(
        "filter1",
        "end",
        Edge("filter1", "end", Some(FilterTrue))
      ),
      "Edge from 'start' to 'end'" -> EdgeNotPresentInOther(
        "start",
        "end",
        Edge("start", "end", None)
      )
    )
  }

  test("detect changed nodes") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.filter("filter1", "#input == 8".spel).emptySink("end", "testSink"))

    ScenarioGraphComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeDifferent(
        "filter1",
        Filter("filter1", "#input == 4".spel),
        Filter("filter1", "#input == 8".spel)
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
        Edge("switch1", "end1", Some(NextSwitch("current".spel))),
        Edge("switch1", "end1", Some(NextSwitch("other".spel)))
      )
    )
  }

  test("describeMeaningfulDiffs returns empty for identical graphs") {
    val current = toDisplayable(_.emptySink("end", "testSink"))
    val diff    = ScenarioGraphComparator.compare(current, current)
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) shouldBe empty
  }

  test("describeMeaningfulDiffs describes a node content change") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.filter("filter1", "#input == 8".spel).emptySink("end", "testSink"))
    val diff    = ScenarioGraphComparator.compare(current, other)
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) shouldBe List("Node 'filter1' modified")
  }

  test("describeMeaningfulDiffs is empty when only layoutData differs") {
    val layoutA = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(10, 20))))
    val layoutB = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(99, 99))))
    val nodeWithLayoutA = Filter("filter1", "#input == 4".spel, additionalFields = layoutA)
    val nodeWithLayoutB = Filter("filter1", "#input == 4".spel, additionalFields = layoutB)
    val diff            = Map("Node 'filter1'" -> NodeDifferent("filter1", nodeWithLayoutA, nodeWithLayoutB))
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) shouldBe empty
  }

  test("describeMeaningfulDiffs describes the change when layout and content both differ") {
    val layoutA = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(10, 20))))
    val layoutB = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(99, 99))))
    val nodeWithLayoutA = Filter("filter1", "#input == 4".spel, additionalFields = layoutA)
    val nodeWithLayoutB = Filter("filter1", "#input == 8".spel, additionalFields = layoutB)
    val diff            = Map("Node 'filter1'" -> NodeDifferent("filter1", nodeWithLayoutA, nodeWithLayoutB))
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) shouldBe List("Node 'filter1' modified")
  }

  test("describeMeaningfulDiffs describes added and removed nodes and edges, regardless of layout") {
    val current = toDisplayable(_.filter("filter1", "#input == 4".spel).emptySink("end", "testSink"))
    val other   = toDisplayable(_.emptySink("end", "testSink"))
    val diff    = ScenarioGraphComparator.compare(current, other)
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) should contain theSameElementsAs List(
      "Node 'filter1' added",
      "Edge 'start' → 'filter1' added",
      "Edge 'filter1' → 'end' added",
      "Edge 'start' → 'end' removed"
    )
  }

  test("describeMeaningfulDiffs describes an edge change") {
    val current = toDisplayable(_.switch("switch1", "#input".spel, "var", caseWithExpression("current")))
    val other   = toDisplayable(_.switch("switch1", "#input".spel, "var", caseWithExpression("other")))
    val diff    = ScenarioGraphComparator.compare(current, other)
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) shouldBe List("Edge 'switch1' → 'end1' modified")
  }

  test("describeMeaningfulDiffs is empty when only a sticky note's layoutData differs") {
    val layoutA = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(10, 20))))
    val layoutB = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(99, 99))))
    val noteWithLayoutA = StickyNote("note1", "content", "#fff", Dimensions(200, 100), layoutA)
    val noteWithLayoutB = StickyNote("note1", "content", "#fff", Dimensions(200, 100), layoutB)
    val diff            = Map("Node 'note1'" -> StickyNoteDifferent("note1", noteWithLayoutA, noteWithLayoutB))
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) shouldBe empty
  }

  test("describeMeaningfulDiffs describes a sticky note content change") {
    val layoutA = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(10, 20))))
    val layoutB = Some(UserDefinedAdditionalNodeFields(description = None, layoutData = Some(LayoutData(99, 99))))
    val noteWithLayoutA = StickyNote("note1", "content A", "#fff", Dimensions(200, 100), layoutA)
    val noteWithLayoutB = StickyNote("note1", "content B", "#fff", Dimensions(200, 100), layoutB)
    val diff            = Map("Node 'note1'" -> StickyNoteDifferent("note1", noteWithLayoutA, noteWithLayoutB))
    ScenarioGraphComparator.describeMeaningfulDiffs(diff) shouldBe List("Note 'note1' modified")
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
