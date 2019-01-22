package pl.touk.nussknacker.ui.validation

import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.ui.definition.AdditionalProcessProperty
import pl.touk.nussknacker.ui.api.helpers.TestFactory.sampleResolver
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory, TestProcessingTypes}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.EdgeType.{NextSwitch, SwitchDefault}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessProperties}
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.{Edge, EdgeType, Group, ProcessAdditionalFields}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationErrors, ValidationResult, ValidationWarnings}

class ProcessValidationSpec extends FlatSpec with Matchers {
  import pl.touk.nussknacker.ui.definition.PropertyType._

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
        ValidationWarnings.success,
        //TODO: add typing results in this case
        _
      ) if nodes == Map("subIn" -> List(PrettyValidationErrors.nonuniqeEdge(validator.uiValidationError,
          EdgeType.SubprocessOutput("out2")))) =>
    }
  }

  it should "check for duplicates in groups" in {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Variable("var", "var1", Expression("spel", "0")),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(
        Edge("in", "var", None),
        Edge("var", "out", None)
      ),
      groups = Set(Group("in", Set("in", "var1")))
    )
    validator.validate(process) should matchPattern {
      case ValidationResult(
        ValidationErrors(_, Nil, globalErrors),
        ValidationWarnings.success,
        //TODO: add typing results in this case
        _
      ) if globalErrors == List(PrettyValidationErrors.duplicatedNodeIds(validator.uiValidationError, List("in"))) =>
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
        ValidationWarnings.success,
        //TODO: add typing results in this case
        _
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
        ValidationWarnings.success,
        //TODO: add typing results in this case
        _
      ) if nodes.isEmpty && global == PrettyValidationErrors.tooManySources(validator.uiValidationError, List("in", "tooMany")) =>
    }
  }

  it should "check for duplicated ids" in {
    val process = createProcess(
      List(
        Source("inID", SourceRef("barSource", List())),
        Filter("inID", Expression("spel", "''")),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("inID", "inID", None), Edge("inID", "out", None))
    )
    val result = validator.validate(process)

    result.errors.globalErrors shouldBe List(PrettyValidationErrors.duplicatedNodeIds(validator.uiValidationError, List("inID")))
    result.errors.invalidNodes shouldBe empty
    result.warnings shouldBe ValidationWarnings.success
  }

  it should "check for duplicated ids when duplicated id is switch id" in {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Switch("switchID", Expression("spel", "''"), "expr1"),
        Sink("out", SinkRef("barSink", List())),
        Sink("switchID", SinkRef("barSink", List()))
      ),
      List(
        Edge("in", "switchID", None),
        Edge("switchID", "out", Some(SwitchDefault)),
        Edge("switchID", "switch", Some(NextSwitch(Expression("spel", "''"))))
      )
    )
    val result = validator.validate(process)

    result.errors.globalErrors shouldBe List(PrettyValidationErrors.duplicatedNodeIds(validator.uiValidationError, List("switchID")))
    result.errors.invalidNodes shouldBe empty
    result.warnings shouldBe ValidationWarnings.success
  }

  it should "not fail with exception when no processtype validator present" in {
    val process = createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("in", "out", None)), `type` = TestProcessingTypes.RequestResponse
    )
    validator.validate(process) should matchPattern {
      case ValidationResult(
        ValidationErrors(_, Nil, errors),
        ValidationWarnings.success,
        _
      ) if errors == List(PrettyValidationErrors.noValidatorKnown(TestProcessingTypes.RequestResponse)) =>
    }
  }

  it should "not allow required process fields" in {
    val processValidation = new ProcessValidation(Map(TestProcessingTypes.Streaming -> ProcessTestData.validator),
      Map(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalProcessProperty("label1", string, None, true, None),
        "field2" -> AdditionalProcessProperty("label2", string, None, false, None)
      )), sampleResolver)

    processValidation.validate(validProcessWithFields(Map("field1" -> "a", "field2" -> "b"))) shouldBe 'ok

    processValidation.validate(validProcessWithFields(Map("field1" -> "a"))) shouldBe 'ok

    processValidation.validate(validProcessWithFields(Map("field1" -> "", "field2" -> "b")))
      .errors.processPropertiesErrors shouldBe List(NodeValidationError("UiValidation", "Field field1 (label1) cannot be empty",
      "label1 cannot be empty", Some("field1"), ValidationResults.NodeValidationErrorType.SaveAllowed))
    
    processValidation.validate(validProcessWithFields(Map("field2" -> "b")))
      .errors.processPropertiesErrors shouldBe List(NodeValidationError("UiValidation", "Field field1 (label1) cannot be empty",
      "label1 cannot be empty", Some("field1"), ValidationResults.NodeValidationErrorType.SaveAllowed))

  }

  it should "validate type in process field" in {
    val processValidation = new ProcessValidation(Map(TestProcessingTypes.Streaming -> ProcessTestData.validator),
      Map(TestProcessingTypes.Streaming -> Map(
        "field1" -> AdditionalProcessProperty("label", select, None, isRequired = false, values = Some("true" :: "false" :: Nil)),
        "field2" -> AdditionalProcessProperty("label", integer, None, isRequired = false, None)
      )), sampleResolver)

    processValidation.validate(validProcessWithFields(Map("field1" -> "true"))) shouldBe 'ok
    processValidation.validate(validProcessWithFields(Map("field1" -> "false"))) shouldBe 'ok
    processValidation.validate(validProcessWithFields(Map("field1" -> "1"))) should not be 'ok

    processValidation.validate(validProcessWithFields(Map("field2" -> "1"))) shouldBe 'ok
    processValidation.validate(validProcessWithFields(Map("field2" -> "1.1"))) should not be 'ok
    processValidation.validate(validProcessWithFields(Map("field2" -> "true"))) should not be 'ok
  }

  it should "handle unknown properties validation" in {
    val processValidation = new ProcessValidation(Map(TestProcessingTypes.Streaming -> ProcessTestData.validator),
      Map(TestProcessingTypes.Streaming -> Map(
        "field2" -> AdditionalProcessProperty("label", integer, None, isRequired = false, None)
      )), sampleResolver)

    processValidation.validate(validProcessWithFields(Map("field1" -> "true"))) should not be 'ok

  }

  private def createProcess(nodes: List[NodeData],
                            edges: List[Edge],
                            `type`: ProcessingTypeData.ProcessingType = TestProcessingTypes.Streaming,
                            groups: Set[Group] = Set(), additionalFields: Map[String, String] = Map()) = {
    DisplayableProcess("test", ProcessProperties(StreamMetaData(),
      ExceptionHandlerRef(List()), subprocessVersions = Map.empty, additionalFields = Some(ProcessAdditionalFields(None, groups, additionalFields))), nodes, edges, `type`)
  }

  private def validProcessWithFields(fields: Map[String, String]) = {
    createProcess(
      List(
        Source("in", SourceRef("barSource", List())),
        Sink("out", SinkRef("barSink", List()))
      ),
      List(Edge("in", "out", None)), additionalFields = fields
    )
  }
}
