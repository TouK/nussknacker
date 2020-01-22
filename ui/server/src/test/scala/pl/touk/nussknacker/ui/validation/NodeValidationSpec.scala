package pl.touk.nussknacker.ui.validation

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.{Group, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.{NodeData, Processor, Sink, Source, Variable, VariableBuilder}
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.Edge
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties, UiNodeContext}
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.api.helpers.{TestFactory, TestProcessingTypes}

class NodeValidationSpec extends FunSuite with Matchers {

  private val processValidator = TestFactory.processValidation

  private val validationResources = TestFactory.processResolving

  test("Extract context") {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Variable("a", "var1", Expression("spel", "'abc'")),
        Variable("b", "var2", Expression("spel", "#var1.length()")),
        VariableBuilder("c", "builder", List(Field("foo1", Expression("spel", "#var2 * 3")), Field("foo2", Expression("spel", "#var1.toUpperCase()")))),
        Variable("d", "var3", Expression("spel", "#var22 + 1 + 'abc'")),
        Processor("e", ServiceRef("fooProcessor", List())),
        Sink("out", SinkRef("barSink", List()), Option(Expression("spel", "#builder.foo2")))
      ),
      List(
        Edge("in", "a", None),
        Edge("a", "b", None),
        Edge("b", "c", None),
        Edge("c", "d", None),
        Edge("d", "e", None),
        Edge("e", "out", None),
      )
    )

    val validationResult = processValidator.validate(process)

    val nodeValidationRes = process.nodes
      .map(nodeData => nodeData.id)
      .map(id => (id, processValidator.validateNode(getNodeContext(id, process, validationResult))))

    nodeValidationRes.size should equal(process.nodes.size)
  }

  private def getNodeContext(nodeId: String, process: DisplayableProcess, validationResult: ValidationResult): UiNodeContext = {
    val nodeData = process.nodes.find(_.id == nodeId)
    val nodeContext = validationResult.variableTypes.find(_._1 == nodeId)
    UiNodeContext(nodeData.get, nodeContext.get._2, process.processingType)
  }

  private def createProcess(nodes: List[NodeData],
                            edges: List[Edge],
                            `type`: ProcessingTypeData.ProcessingType = TestProcessingTypes.Streaming,
                            groups: Set[Group] = Set(), additionalFields: Map[String, String] = Map()) = {
    DisplayableProcess("test", ProcessProperties(StreamMetaData(),
      ExceptionHandlerRef(List()), subprocessVersions = Map.empty, additionalFields = Some(ProcessAdditionalFields(None, groups, additionalFields))), nodes, edges, `type`)
  }
}
