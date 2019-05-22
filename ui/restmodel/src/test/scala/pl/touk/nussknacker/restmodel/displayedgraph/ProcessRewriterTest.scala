package pl.touk.nussknacker.restmodel.displayedgraph

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.{Filter, Sink, Source, Variable}
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{FilterFalse, FilterTrue}


class ProcessRewriterTest extends FunSuite with Matchers {
  import spel.Implicits._

  val properties =
    ProcessProperties(StreamMetaData(), ExceptionHandlerRef(List.empty), subprocessVersions = Map.empty)

  val source = Source("in", SourceRef("barSource", List()))
  val filter = Filter("filter", "true", isDisabled = Some(true))
  val variable = Variable("var", "var", "")
  val sink1 =  Sink("out1", SinkRef("barSink", List()))
  val sink2 =  Sink("out2", SinkRef("barSink", List()))

  val process = DisplayableProcess(
    id = "",
    properties,
    nodes = List(source, filter, variable, sink1, sink2),
    edges = List(
      Edge(from = "in", to = "filter", edgeType = None),
      Edge(from = "filter", to = "var", edgeType = Some(FilterTrue)),
      Edge(from = "var", to = "out1", edgeType = None),
      Edge(from = "filter", to = "out2", edgeType = Some(FilterFalse))),
    processingType = "")

  test(".removeAndBypassNode") {
    val rewrited = ProcessRewriter.removeAndBypassNode(process, filter)

    rewrited.nodes shouldBe List(source, variable, sink1)

    rewrited.edges shouldBe List(
      Edge(from = "in", to = "var", edgeType = None),
      Edge(from = "var", to = "out1", edgeType = None))
  }

  test(".removeBranch") {
    val rewrited = ProcessRewriter.removeBranch(process, variable)

    rewrited.nodes shouldBe List(source, filter, sink2)

    rewrited.edges shouldBe List(
      Edge(from = "in", to = "filter", edgeType = None),
      Edge(from = "filter", to = "out2", edgeType = Some(FilterFalse)))
  }

  test(".removeDisabledNodes") {
    val rewrited = ProcessRewriter.removeDisabledNodes(process)

    rewrited.nodes shouldBe List(source, variable, sink1)

    rewrited.edges shouldBe List(
      Edge(from = "in", to = "var", edgeType = None),
      Edge(from = "var", to = "out1", edgeType = None))
  }
}
