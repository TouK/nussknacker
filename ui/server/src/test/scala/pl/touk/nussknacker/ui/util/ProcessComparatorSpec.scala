package pl.touk.nussknacker.ui.util

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.util.ProcessComparator.{NodeDifferent, NodeNotPresentInCurrent, NodeNotPresentInOther}

//TODO: tests for changed properties and edges
class ProcessComparatorSpec extends FlatSpec with Matchers {

  import pl.touk.nussknacker.engine.spel.Implicits._

  it should "detect not existing node in other process" in {
    val current = toDisplayable(_.filter("filter1", "#input == 4").sink("end", "testSink"))
    val other = toDisplayable(_.sink("end", "testSink"))

    ProcessComparator.compare(current, other) shouldBe Map("filter1" -> NodeNotPresentInOther("filter1", Filter("filter1", "#input == 4")))

  }

  it should "detect not existing node in current process" in {
    val current = toDisplayable(_.sink("end", "testSink"))
    val other = toDisplayable(_.filter("filter1", "#input == 4").sink("end", "testSink"))

    ProcessComparator.compare(current, other) shouldBe Map("filter1" -> NodeNotPresentInCurrent("filter1", Filter("filter1", "#input == 4")))
  }

  it should "detect changed nodes" in {
    val current = toDisplayable(_.filter("filter1", "#input == 4").sink("end", "testSink"))
    val other = toDisplayable(_.filter("filter1", "#input == 8").sink("end", "testSink"))

    ProcessComparator.compare(current, other) shouldBe Map("filter1" -> NodeDifferent("filter1", Filter("filter1", "#input == 4"), Filter("filter1", "#input == 8")))
  }


  private def toDisplayable(espProcess: GraphBuilder[EspProcess] => EspProcess) : DisplayableProcess  =
    toDisplayableFromProcess(espProcess( EspProcessBuilder.id("test").parallelism(1).exceptionHandler().source("start", "testSource")))

  private def toDisplayableFromProcess(espProcess: EspProcess) : DisplayableProcess =
    ProcessConverter.toDisplayable(ProcessCanonizer.canonize(espProcess), ProcessingType.Streaming)


}
