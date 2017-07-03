package pl.touk.esp.ui.validation

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.api.StreamMetaData
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.subprocess.SubprocessRef
import pl.touk.esp.ui.api.helpers.TestFactory
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.esp.ui.process.displayedgraph.displayablenode.{Edge, EdgeType}
import pl.touk.esp.ui.validation.ValidationResults.{NodeValidationError, ValidationErrors, ValidationResult, ValidationWarnings}

import scala.collection.Map
import scala.collection.immutable.Map.EmptyMap

class ProcessValidationSpec extends FlatSpec with Matchers {

  private val validator = TestFactory.processValidation

  it should "check for notunique edges" in {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        SubprocessInput("subIn", SubprocessRef("sub1", List())),
        Sink("out", SinkRef("barSink", List())),
        Sink("out2", SinkRef("barSink", List())),
        Sink("out3", SinkRef("barSink", List()))
      ),
      List(
        Edge("in", "subIn", None),
        Edge("subIn", "out", Some(EdgeType.SubprocessOutput("out1"))),
        Edge("subIn", "out2", Some(EdgeType.SubprocessOutput("out2"))),
        Edge("subIn", "out3", Some(EdgeType.SubprocessOutput("out2")))
      )

    )
    validator.validate(process) should matchPattern {
      case ValidationResult(
        ValidationErrors(nodes, Nil, Nil),
        ValidationWarnings.success
      ) if nodes == Map("subIn" -> List(PrettyValidationErrors.nonuniqeEdge(validator.uiValidationError,
          EdgeType.SubprocessOutput("out2")))) =>
    }
  }

  it should "check for loose nodes" in {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Sink("out", SinkRef("barSink", List())),
        Filter("loose", Expression("spel", "true"))
      ),
      List(Edge("in", "out", None))

    )
    validator.validate(process) should matchPattern {
      case ValidationResult(
        ValidationErrors(nodes, Nil, Nil),
        ValidationWarnings.success
      ) if nodes == Map("loose" -> List(PrettyValidationErrors.looseNode(validator.uiValidationError))) =>
    }

  }

  it should "check for mulitple inputs" in {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Sink("out", SinkRef("barSink", List())),
        Sink("out2", SinkRef("barSink", List())),
        Source("tooMany", SourceRef("barSource", List()))
      ),
      List(Edge("in", "out", None), Edge("tooMany", "out2", None))
    )
    validator.validate(process) should matchPattern {
      case ValidationResult(
        ValidationErrors(nodes, Nil, global::Nil),
        ValidationWarnings.success
      ) if nodes.isEmpty && global == PrettyValidationErrors.tooManySources(validator.uiValidationError, List("in", "tooMany")) =>
    }



  }

  private def createProcess(nodes: List[NodeData], edges: List[Edge]) = {
    DisplayableProcess("test", ProcessProperties(StreamMetaData(),
      ExceptionHandlerRef(List())), nodes, edges, ProcessingType.Streaming)
  }


}
