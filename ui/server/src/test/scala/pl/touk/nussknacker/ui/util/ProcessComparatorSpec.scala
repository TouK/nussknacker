package pl.touk.nussknacker.ui.util

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.{Case, Filter}
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{FilterTrue, NextSwitch}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, EdgeType}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.util.ProcessComparator.{EdgeDifferent, EdgeNotPresentInCurrent, EdgeNotPresentInOther, NodeDifferent, NodeNotPresentInCurrent, NodeNotPresentInOther}

//TODO: tests for changed properties
class ProcessComparatorSpec extends FunSuite with Matchers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  test("detect not existing node in other process") {
    val current = toDisplayable(_.filter("filter1", "#input == 4").emptySink("end", "testSink"))
    val other = toDisplayable(_.emptySink("end", "testSink"))

    ProcessComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeNotPresentInOther("filter1", Filter("filter1", "#input == 4")),
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
    val other = toDisplayable(_.filter("filter1", "#input == 4").emptySink("end", "testSink"))

    ProcessComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeNotPresentInCurrent("filter1", Filter("filter1", "#input == 4")),
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
    val current = toDisplayable(_.filter("filter1", "#input == 4").emptySink("end", "testSink"))
    val other = toDisplayable(_.filter("filter1", "#input == 8").emptySink("end", "testSink"))

    ProcessComparator.compare(current, other) shouldBe Map(
      "Node 'filter1'" -> NodeDifferent("filter1", Filter("filter1", "#input == 4"), Filter("filter1", "#input == 8"))
    )
  }

  test("detect changed edges") {
    val current = toDisplayable(_.switch("switch1", "#input", "var", caseWithExpression("current")))
    val other = toDisplayable(_.switch("switch1", "#input", "var", caseWithExpression("other")))

    ProcessComparator.compare(current, other) shouldBe Map(
      "Edge from 'switch1' to 'end1'" -> EdgeDifferent(
        "switch1",
        "end1",
        Edge("switch1", "end1", Some(NextSwitch("current"))),
        Edge("switch1", "end1", Some(NextSwitch("other")))
      )
    )
  }

  private def toDisplayable(espProcess: GraphBuilder[EspProcess] => EspProcess) : DisplayableProcess  =
    toDisplayableFromProcess(espProcess(EspProcessBuilder.id("test").parallelism(1).exceptionHandler().source("start", "testSource")))

  private def toDisplayableFromProcess(espProcess: EspProcess) : DisplayableProcess =
    ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), TestProcessingTypes.Streaming)

  private def caseWithExpression(expr: String, id: Int = 1): Case = {
    Case(expr, GraphBuilder.emptySink(s"end$id", "end"))
  }
}
