package pl.touk.nussknacker.engine.compile

import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentDefinition,
  DesignerWideComponentId,
  ParameterAdditionalUIConfig
}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.dict.embedded.EmbeddedDictDefinition
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterName,
  ParameterValueCompileTimeValidation,
  ValueInputWithDictEditor,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ExpressionConfig, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  NodeDataValidator,
  ValidationNotPerformed,
  ValidationPerformed,
  ValidationResponse
}
import pl.touk.nussknacker.engine.compile.validationHelpers._
import pl.touk.nussknacker.engine.definition.component.parameter.validator.ValidationExpressionParameterValidator
import pl.touk.nussknacker.engine.graph.EdgeType.{FragmentOutput, NextSwitch}
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.expression.{Expression, NodeExpressionId}
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.jdk.CollectionConverters._

class NodeDataValidatorSpec extends AnyFunSuite with Matchers with Inside with TableDrivenPropertyChecks {

  private val defaultConfig: Config = List("genericParametersSource", "genericParametersSink", "genericTransformer")
    .foldLeft(ConfigFactory.empty())((c, n) =>
      c
        .withValue(s"componentsUiConfig.$n.params.par1.defaultValue", fromAnyRef("'realDefault'"))
        .withValue(s"componentsUiConfig.$n.params.par1.label", fromAnyRef("Parameter 1"))
    )

  private val defaultFragmentId: String = "fragment1"

  private val defaultFragmentDef: CanonicalProcess = CanonicalProcess(
    MetaData(defaultFragmentId, FragmentSpecificData()),
    List(
      FlatNode(
        FragmentInputDefinition("in", List(FragmentParameter(ParameterName("param1"), FragmentClazzRef[String])))
      ),
      FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'".spel)))),
    )
  )

  private val defaultFragmentOutgoingEdges: List[OutgoingEdge] = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))

  def getModelData(aConfig: Config = defaultConfig): LocalModelData = {
    LocalModelData(
      aConfig,
      List(
        ComponentDefinition("genericJoin", DynamicParameterJoinTransformer),
        ComponentDefinition("genericTransformer", GenericParametersTransformer),
        ComponentDefinition(
          "genericTransformerUsingParameterValidator",
          GenericParametersTransformerUsingParameterValidator
        ),
        ComponentDefinition("stringService", SimpleStringService),
        ComponentDefinition("genericParametersThrowingException", GenericParametersThrowingException),
        ComponentDefinition("missingParamHandleGenericNodeTransformation", MissingParamHandleDynamicComponent$),
        ComponentDefinition("genericParametersSource", new GenericParametersSource),
        ComponentDefinition("genericParametersSink", GenericParametersSink),
        ComponentDefinition("optionalParameterService", OptionalParameterService)
      ),
      additionalConfigsFromProvider = Map(
        DesignerWideComponentId("streaming-service-optionalParameterService") -> ComponentAdditionalConfig(
          parameterConfigs = Map(
            ParameterName("optionalParam") -> ParameterAdditionalUIConfig(required = true, None, None, None, None)
          )
        )
      ),
      configCreator = new EmptyProcessConfigCreator {
        override def expressionConfig(
            modelDependencies: ProcessObjectDependencies
        ): ExpressionConfig =
          ExpressionConfig(Map.empty, List.empty, dictionaries = Map("someDictId" -> EmbeddedDictDefinition(Map.empty)))
      }
    )
  }

  private val modelData = getModelData()

  test("should validate sink factory") {
    validate(
      Sink(
        "tst1",
        SinkRef(
          "genericParametersSink",
          List(
            par("par1", "'a,b'"),
            par("lazyPar1", "#aVar + 3"),
            par("a", "'a'"),
            par("b", "'dd'")
          )
        )
      ),
      ValidationContext(Map("aVar" -> Typed[Long]))
    ) shouldBe ValidationPerformed(Nil, Some(genericParameters), None)

    inside(
      validate(
        Sink(
          "tst1",
          SinkRef(
            "genericParametersSink",
            List(par("par1", "'a,b'"), par("lazyPar1", "#aVar + ''"), par("a", "'a'"), par("b", "''"))
          )
        ),
        ValidationContext(Map("aVar" -> Typed[String]))
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, Some(params), _) =>
      params shouldBe genericParameters
      error.message shouldBe "Bad expression type, expected: Long, found: String"
    }

    validate(Sink("tst1", SinkRef("doNotExist", Nil)), ValidationContext()) should matchPattern {
      case ValidationPerformed((_: MissingSinkFactory) :: Nil, _, _) =>
    }

  }

  test("should validate source factory") {
    validate(
      Source(
        "tst1",
        SourceRef(
          "genericParametersSource",
          List(par("par1", "'a,b'"), par("lazyPar1", "11"), par("a", "'a'"), par("b", "'b'"))
        )
      ),
      ValidationContext()
    ) shouldBe ValidationPerformed(Nil, Some(genericParameters), None)

    inside(
      validate(
        Source(
          "tst1",
          SourceRef(
            "genericParametersSource",
            List(par("par1", "'a,b'"), par("lazyPar1", "''"), par("a", "'a'"), par("b", "''"))
          )
        ),
        ValidationContext()
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, _, _) =>
      error.message shouldBe s"Bad expression type, expected: Long, found: ${Typed.fromInstance("").display}"
    }

    validate(Source("tst1", SourceRef("doNotExist", Nil)), ValidationContext()) should matchPattern {
      case ValidationPerformed((_: MissingSourceFactory) :: Nil, _, _) =>
    }

  }

  test("should validate filter") {
    inside(validate(Filter("filter", "#a > 3".spel), ValidationContext(Map("a" -> Typed[String])))) {
      case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, None, _) =>
        error.message shouldBe "Wrong part types"
    }
  }

  test("should not allow null values in filter") {
    forAll(ExpressionsTestData.nullExpressions) { filterExpression =>
      validate(Filter("filter", filterExpression.spel), ValidationContext.empty) should matchPattern {
        case ValidationPerformed(
              (
                EmptyMandatoryParameter(
                  "This field is required and can not be null",
                  _,
                  NodeExpressionId.DefaultExpressionIdParamName,
                  "filter"
                )
              ) :: Nil,
              _,
              _
            ) =>
      }
    }
  }

  test("should validate service") {
    inside(
      validate(
        node.Enricher("stringService", ServiceRef("stringService", List(par("stringParam", "#a.length + 33"))), "out"),
        ValidationContext(Map("a" -> Typed[String]))
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, None, _) =>
      error.message shouldBe "Bad expression type, expected: String, found: Integer"
    }

    validate(Processor("tst1", ServiceRef("doNotExist", Nil)), ValidationContext()) should matchPattern {
      case ValidationPerformed((_: MissingService) :: Nil, _, _) =>
    }
  }

  test("should validate custom node") {
    inside(
      validate(
        CustomNode(
          "tst1",
          Some("out"),
          "genericTransformer",
          List(par("par1", "'a,b'"), par("lazyPar1", "#aVar + ''"), par("a", "'a'"), par("b", "''"))
        ),
        ValidationContext(Map("aVar" -> Typed[String]))
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, Some(params), _) =>
      params shouldBe genericParameters
      error.message shouldBe "Bad expression type, expected: Long, found: String"
    }

    validate(CustomNode("tst1", None, "doNotExist", Nil), ValidationContext()) should matchPattern {
      case ValidationPerformed((_: MissingCustomNodeExecutor) :: Nil, _, _) =>
    }
  }

  test("should validate transformer using parameter validator") {
    inside(
      validate(
        CustomNode("tst1", None, "genericTransformerUsingParameterValidator", List(par("paramWithFixedValues", "666"))),
        ValidationContext.empty
      )
    ) { case ValidationPerformed(InvalidPropertyFixedValue(_, _, "666", _, _) :: Nil, _, _) =>
    }
  }

  test("should handle exception throws during validation gracefully") {
    inside(
      validate(
        node.Processor(
          "tst1",
          ServiceRef(
            "genericParametersThrowingException",
            List(
              par("par1", "'val1,val2,val3'"),
              par("lazyPar1", "#input == null ? 1 : 5"),
              par("val1", "'aa'"),
              par("val2", "11"),
              par("val3", "{false}")
            )
          )
        ),
        ValidationContext(Map("input" -> Typed[String]))
      )
    ) {
      case ValidationPerformed(CannotCreateObjectError("Some exception", "tst1") :: Nil, parameters, _)
          if parameters.nonEmpty =>
    }
  }

  test("should handle missing parameters handle in transformation") {
    val expectedError = WrongParameters(Set.empty, Set(ParameterName("param1")))(NodeId("fooNode"))
    inside(
      validate(
        node.Processor(
          "fooNode",
          ServiceRef(
            "missingParamHandleGenericNodeTransformation",
            List(par("param1", "'foo'"))
          )
        ),
        ValidationContext.empty
      )
    ) { case ValidationPerformed(err :: Nil, _, _) =>
      err shouldBe expectedError
    }
  }

  test("should allow user variable") {
    inside(validate(Variable("var1", "specialVariable_2", "42L".spel, None), ValidationContext())) {
      case ValidationPerformed(Nil, None, _) =>
    }
  }

  test("should validate empty or blank variable expression") {
    forAll(ExpressionsTestData.emptyOrBlankExpressions) { e =>
      validate(Variable("var1", "specialVariable_2", e.spel), ValidationContext.empty) should matchPattern {
        case ValidationPerformed(
              (
                EmptyMandatoryParameter(
                  "This field is mandatory and can not be empty",
                  _,
                  NodeExpressionId.DefaultExpressionIdParamName,
                  "var1"
                )
              ) :: Nil,
              _,
              _
            ) =>
      }
    }
  }

  test("should validate variable definition") {
    inside(
      validate(Variable("var1", "var1", "doNotExist".spel, None), ValidationContext(Map.empty))
    ) { case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, None, _) =>
      error.message shouldBe "Non reference 'doNotExist' occurred. Maybe you missed '#' in front of it?"
    }
  }

  test("should not allow to override output variable in variable definition") {
    inside(
      validate(
        Variable("var1", "var1", "42L".spel, None),
        ValidationContext(localVariables = Map("var1" -> typing.Unknown))
      )
    ) { case ValidationPerformed(OverwrittenVariable("var1", "var1", _) :: Nil, None, _) =>
    }
  }

  test("should not allow to use special chars in variable name") {
    inside(validate(Variable("var1", "var@ 2", "42L".spel, None), ValidationContext())) {
      case ValidationPerformed(InvalidVariableName("var@ 2", "var1", _) :: Nil, None, _) =>
    }
  }

  test("should return expression type info for variable definition") {
    inside(validate(Variable("var1", "var1", "42L".spel, None), ValidationContext(Map.empty))) {
      case ValidationPerformed(Nil, _, Some(expressionType)) =>
        expressionType.display shouldBe Typed.fromInstance(42L).display
    }
  }

  test("should validate variable builder definition") {
    inside(
      validate(
        VariableBuilder("var1", "var1", List(Field("field1", "doNotExist".spel)), None),
        ValidationContext(Map.empty)
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, None, _) =>
      error.message shouldBe "Non reference 'doNotExist' occurred. Maybe you missed '#' in front of it?"
    }
  }

  test("should not allow to override output variable in variable builder definition") {
    inside(
      validate(
        VariableBuilder("var1", "var1", Nil, None),
        ValidationContext(localVariables = Map("var1" -> typing.Unknown))
      )
    ) { case ValidationPerformed(OverwrittenVariable("var1", "var1", _) :: Nil, None, _) =>
    }
  }

  test("should not allow duplicated field names in variable builder") {
    inside(
      validate(
        VariableBuilder("recordVariable", "var1", Field("field", "null".spel) :: Field("field", "null".spel) :: Nil),
        ValidationContext.empty
      )
    ) {
      case ValidationPerformed(
            CustomParameterValidationError(
              "The key of a record has to be unique",
              _,
              ParameterName("$fields-0-$key"),
              "recordVariable"
            ) :: CustomParameterValidationError(
              "The key of a record has to be unique",
              _,
              ParameterName("$fields-1-$key"),
              "recordVariable"
            ) :: Nil,
            None,
            _
          ) =>
    }
  }

  test("should not allow duplicated field names in variable builder when cannot compile") {
    inside(
      validate(
        VariableBuilder(
          "recordVariable",
          "var1",
          Field("field", "unresolvedReference".spel) :: Field("field", "null".spel) :: Nil
        ),
        ValidationContext.empty
      )
    ) {
      case ValidationPerformed(
            ExpressionParserCompilationError(
              "Non reference 'unresolvedReference' occurred. Maybe you missed '#' in front of it?",
              "recordVariable",
              Some(ParameterName("$fields-0-$value")),
              "unresolvedReference",
              None
            ) ::
            CustomParameterValidationError(
              "The key of a record has to be unique",
              _,
              ParameterName("$fields-0-$key"),
              "recordVariable"
            ) :: CustomParameterValidationError(
              "The key of a record has to be unique",
              _,
              ParameterName("$fields-1-$key"),
              "recordVariable"
            ) :: Nil,
            None,
            _
          ) =>
    }
  }

  test("should not allow empty values in variable builder") {
    forAll(ExpressionsTestData.emptyOrBlankExpressions) { e =>
      inside(
        validate(
          VariableBuilder("recordVariable", "var1", Field("field1", e.spel) :: Field("field2", e.spel) :: Nil),
          ValidationContext.empty
        )
      ) {
        case ValidationPerformed(
              EmptyMandatoryParameter(
                "This field is mandatory and can not be empty",
                _,
                ParameterName("$fields-0-$value"),
                "recordVariable"
              ) :: EmptyMandatoryParameter(
                "This field is mandatory and can not be empty",
                _,
                ParameterName("$fields-1-$value"),
                "recordVariable"
              ) :: Nil,
              None,
              _
            ) =>
      }
    }
  }

  test("should return inferred type for variable builder output") {
    inside(
      validate(
        VariableBuilder("var1", "var1", List(Field("field1", "42L".spel), Field("field2", "'some string'".spel)), None),
        ValidationContext(Map.empty)
      )
    ) { case ValidationPerformed(Nil, None, Some(TypedObjectTypingResult(fields, _, _))) =>
      fields.mapValuesNow(_.display) shouldBe Map(
        "field1" -> Typed.fromInstance(42L).display,
        "field2" -> Typed.fromInstance("some string").display
      )
    }
  }

  test("should return inferred type for fragment definition output") {
    inside(
      validate(
        FragmentOutputDefinition(
          "var1",
          "var1",
          List(Field("field1", "42L".spel), Field("field2", "'some string'".spel)),
          None
        ),
        ValidationContext.empty
      )
    ) { case ValidationPerformed(Nil, None, Some(TypedObjectTypingResult(fields, _, _))) =>
      fields.mapValuesNow(_.display) shouldBe Map(
        "field1" -> Typed.fromInstance(42L).display,
        "field2" -> Typed.fromInstance("some string").display
      )
    }
  }

  test("should validate fragment parameters") {
    inside(
      validate(
        FragmentInput(
          "frInput",
          FragmentRef("fragment1", List(NodeParameter(ParameterName("param1"), "145".spel)), Map("out1" -> "test1"))
        ),
        ValidationContext.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(ExpressionParserCompilationError(message, "frInput", Some(ParameterName("param1")), "145", None)),
            None,
            None
          ) =>
        message shouldBe s"Bad expression type, expected: String, found: ${Typed.fromInstance(145).display}"
    }
  }

  test("should validate fragment parameters with validators -  - P1 as mandatory param with some actual value") {
    val defaultFragmentOutgoingEdges: List[OutgoingEdge] = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
    val fragmentId                                       = "fragmentInputId"
    val nodeToBeValidated =
      FragmentInput(
        "nameOfTheNode",
        FragmentRef(fragmentId, List(NodeParameter(ParameterName("P1"), "123".spel)), Map("out1" -> "test1"))
      )
    val fragmentDefinitionWithValidators: CanonicalProcess = CanonicalProcess(
      MetaData(fragmentId, FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter(ParameterName("P1"), FragmentClazzRef[Short])))),
        FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'".spel)))),
      )
    )
    val configWithValidators: Config = defaultConfig.withValue(
      s"componentsUiConfig.$fragmentId.params.P1.validators",
      fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
    )

    validate(
      nodeToBeValidated,
      ValidationContext.empty,
      outgoingEdges = defaultFragmentOutgoingEdges,
      fragmentDefinition = fragmentDefinitionWithValidators,
      aModelData = getModelData(configWithValidators)
    ) should matchPattern { case ValidationPerformed(List(), None, None) =>
    }
  }

  test("should validate fragment parameters with validators - P1 as mandatory param with missing actual value") {
    val fragmentId = "fragmentInputId"
    val nodeId     = "someNodeId"
    val nodeToBeValidated =
      FragmentInput(
        nodeId,
        FragmentRef(fragmentId, List(NodeParameter(ParameterName("P1"), "".spel)), Map("out1" -> "test1"))
      )
    val fragmentDefinitionWithValidators: CanonicalProcess = CanonicalProcess(
      MetaData(fragmentId, FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            "in",
            List(FragmentParameter(ParameterName("P1"), FragmentClazzRef[Short]).copy(required = true))
          )
        ),
        FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'".spel)))),
      )
    )
    val configWithValidators: Config = defaultConfig.withValue(
      s"componentsUiConfig.$fragmentId.params.P1.validators",
      fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
    )

    inside(
      validate(
        nodeToBeValidated,
        ValidationContext.empty,
        outgoingEdges = defaultFragmentOutgoingEdges,
        fragmentDefinition = fragmentDefinitionWithValidators,
        aModelData = getModelData(configWithValidators)
      )
    ) {
      case ValidationPerformed(List(EmptyMandatoryParameter(_, _, ParameterName("P1"), returnedNodeId)), None, None) =>
        returnedNodeId shouldBe nodeId
    }
  }

  test(
    "should validate fragment parameters with validators - P1 and P2 as mandatory params with missing actual values accumulated"
  ) {
    val fragmentId = "fragmentInputId"
    val nodeToBeValidated = FragmentInput(
      "nameOfTheNode",
      FragmentRef(
        fragmentId,
        List(
          NodeParameter(ParameterName("P1"), "".spel),
          NodeParameter(ParameterName("P2"), "".spel),
        ),
        Map("out1" -> "test1")
      )
    )

    val fragmentDefinitionWithValidators: CanonicalProcess = CanonicalProcess(
      MetaData(fragmentId, FragmentSpecificData()),
      List(
        FlatNode(
          FragmentInputDefinition(
            "in",
            List(
              FragmentParameter(ParameterName("P1"), FragmentClazzRef[Short]).copy(required = true),
              FragmentParameter(ParameterName("P2"), FragmentClazzRef[String]).copy(required = true)
            )
          )
        ),
        FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'".spel)))),
      )
    )

    val configWithValidators: Config = defaultConfig
      .withValue(
        s"componentsUiConfig.$fragmentId.params.P1.validators",
        fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
      )
      .withValue(
        s"componentsUiConfig.$fragmentId.params.P2.validators",
        fromIterable(List(Map("type" -> "MandatoryParameterValidator").asJava).asJava)
      )

    validate(
      nodeToBeValidated,
      ValidationContext.empty,
      outgoingEdges = defaultFragmentOutgoingEdges,
      fragmentDefinition = fragmentDefinitionWithValidators,
      aModelData = getModelData(configWithValidators)
    ) should matchPattern {
      case ValidationPerformed(
            List(
              EmptyMandatoryParameter(_, _, ParameterName("P1"), "nameOfTheNode"),
              EmptyMandatoryParameter(_, _, ParameterName("P2"), "nameOfTheNode")
            ),
            None,
            None
          ) =>
    }
  }

  test(
    "should validate service based on additional config from provider - P1 as mandatory param with missing actual value"
  ) {
    val nodeToBeValidated = node.Enricher(
      "enricherNodeId",
      ServiceRef("optionalParameterService", List(NodeParameter(ParameterName("optionalParam"), Expression.spel("")))),
      "out"
    )

    validate(
      nodeToBeValidated,
      ValidationContext.empty,
      outgoingEdges = defaultFragmentOutgoingEdges
    ) should matchPattern {
      case ValidationPerformed(
            List(EmptyMandatoryParameter(_, _, ParameterName("optionalParam"), "enricherNodeId")),
            None,
            None
          ) =>
    }
  }

  test("should validate output parameters") {
    val nodeId = "frInput"
    inside(
      validate(
        FragmentInput(
          nodeId,
          FragmentRef(
            "fragment1",
            List(NodeParameter(ParameterName("param1"), "'someValue'".spel)),
            Map("out1" -> "very bad var name")
          )
        ),
        ValidationContext.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              InvalidVariableName("very bad var name", "frInput", Some(ParameterName("ref.outputVariableNames.out1")))
            ),
            None,
            None
          ) =>
    }

    val existingVar = "var1"
    inside(
      validate(
        FragmentInput(
          nodeId,
          FragmentRef(
            "fragment1",
            List(NodeParameter(ParameterName("param1"), "'someValue'".spel)),
            Map("out1" -> existingVar)
          )
        ),
        ValidationContext(Map(existingVar -> Typed[String])),
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(OverwrittenVariable("var1", "frInput", Some(ParameterName("ref.outputVariableNames.out1")))),
            None,
            None
          ) =>
    }
  }

  test("should validate fragment output edges") {
    val nodeId = "frInput"
    inside(
      validate(
        FragmentInput(
          nodeId,
          FragmentRef(
            "fragment1",
            List(NodeParameter(ParameterName("param1"), "'someValue'".spel)),
            Map("out1" -> "ok")
          )
        ),
        ValidationContext.empty
      )
    ) { case ValidationPerformed(List(FragmentOutputNotDefined("out1", nodes)), None, None) =>
      nodes shouldBe Set(nodeId)
    }

  }

  test("should validate switch") {
    inside(
      validate(
        Switch("switchId", Some("input".spel), Some("value1")),
        ValidationContext.empty,
        Map.empty,
        List(OutgoingEdge("caseTarget1", Some(NextSwitch("notExist".spel))))
      )
    ) {
      case ValidationPerformed(
            List(
              ExpressionParserCompilationError(
                "Non reference 'input' occurred. Maybe you missed '#' in front of it?",
                "switchId",
                Some(ParameterName("$expression")),
                "input",
                None
              ),
              ExpressionParserCompilationError(
                "Non reference 'notExist' occurred. Maybe you missed '#' in front of it?",
                "switchId",
                Some(ParameterName("caseTarget1")),
                "notExist",
                None
              )
            ),
            None,
            Some(Unknown)
          ) =>
    }
  }

  test("should not allow null values in choice expressions") {
    forAll(ExpressionsTestData.nullExpressions) { e =>
      inside(
        validate(
          Switch("switchId", None, None),
          ValidationContext.empty,
          Map.empty,
          List(OutgoingEdge("caseTarget", Some(NextSwitch(e.spel))))
        )
      ) {
        case ValidationPerformed(
              EmptyMandatoryParameter(
                "This field is required and can not be null",
                _,
                ParameterName("caseTarget"),
                "switchId"
              ) :: Nil,
              None,
              None
            ) =>
      }
    }
  }

  test("should validate node id in all cases") {
    forAll(IdValidationTestData.nodeIdErrorCases) { (nodeId: String, expectedErrors: List[ProcessCompilationError]) =>
      validate(Variable(nodeId, "varName", "1".spel, None), ValidationContext()) match {
        case ValidationPerformed(errors, _, _) => errors shouldBe expectedErrors
        case ValidationNotPerformed            => fail("should not happen")
      }
    }
  }

  test("should validate fragment parameter fixed values are of supported type") {
    val nodeId: String = "in"
    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[Int],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = Some(
                ValueInputWithFixedValuesProvided(
                  fixedValuesList = List(FixedExpressionValue("1", "someLabel")),
                  allowOtherValue = false
                )
              ),
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              UnsupportedFixedValuesType(ParameterName("param1"), "int", nodes),
            ),
            None,
            None
          ) =>
        nodes shouldBe Set(nodeId)
    }
  }

  test("should validate initial value outside possible values in FragmentInputDefinition") {
    val nodeId: String = "in"
    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[String],
              required = false,
              initialValue = Some(FixedExpressionValue("'outsidePreset'", "outsidePreset")),
              hintText = None,
              valueEditor = Some(
                ValueInputWithFixedValuesProvided(
                  fixedValuesList = List(FixedExpressionValue("'someValue'", "someValue")),
                  allowOtherValue = false
                )
              ),
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              InitialValueNotPresentInPossibleValues(ParameterName("param1"), nodes)
            ),
            None,
            None
          ) =>
        nodes shouldBe Set(nodeId)
    }
  }

  test("should validate initial value of invalid type in FragmentInputDefinition") {
    val nodeId: String   = "in"
    val stringExpression = "'someString'"

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[java.lang.Boolean],
              required = false,
              initialValue = Some(FixedExpressionValue(stringExpression, "stringButShouldBeBoolean")),
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationErrorInFragmentDefinition) :: Nil, None, None) =>
      error.message should include("Bad expression type, expected: Boolean, found: String(someString)")
    }
  }

  test("should validate fixed value of invalid type in FragmentInputDefinition") {
    val nodeId: String   = "in"
    val stringExpression = "'someString'"

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[java.lang.Boolean],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = Some(
                ValueInputWithFixedValuesProvided(
                  fixedValuesList = List(FixedExpressionValue(stringExpression, "stringButShouldBeBoolean")),
                  allowOtherValue = false
                )
              ),
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationErrorInFragmentDefinition) :: Nil, None, None) =>
      error.message should include("Bad expression type, expected: Boolean, found: String(someString)")
    }

  }

  test("should validate unknown dict id in FragmentInputDefinition") {
    val nodeId: String = "in"

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[java.lang.Boolean],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = Some(
                ValueInputWithDictEditor(
                  dictId = "thisDictDoesntExist",
                  allowOtherValue = false
                )
              ),
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed((error: DictNotDeclared) :: Nil, None, None) =>
      error.dictId shouldBe "thisDictDoesntExist"
    }
  }

  test("should validate dict of invalid type in FragmentInputDefinition") {
    val nodeId: String = "in"

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[java.lang.Boolean],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = Some(
                ValueInputWithDictEditor(
                  dictId = "someDictId",
                  allowOtherValue = false
                )
              ),
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed((error: DictIsOfInvalidType) :: Nil, None, None) =>
      error.expectedType.display shouldBe "Boolean"
      error.actualType.display shouldBe "String @ dictValue:someDictId"
      error.dictId shouldBe "someDictId"
    }
  }

  test("should allow expressions that reference other parameters in FragmentInputDefinition") {
    val nodeId: String        = "in"
    val referencingExpression = "#otherStringParam"

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("otherStringParam"),
              FragmentClazzRef[String],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = None
            ),
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[String],
              required = false,
              initialValue = Some(FixedExpressionValue(referencingExpression, "referencingExpression")),
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed(errors, None, None) =>
      errors shouldBe empty
    }
  }

  test("shouldn't allow expressions that reference unknown variables in FragmentInputDefinition") {
    val nodeId: String               = "in"
    val invalidReferencingExpression = "#unknownVar"

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[String],
              required = false,
              initialValue = Some(FixedExpressionValue(invalidReferencingExpression, "invalidReferencingExpression")),
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed((error: ExpressionParserCompilationErrorInFragmentDefinition) :: Nil, None, None) =>
      error.message should include("Unresolved reference 'unknownVar'")
    }
  }

  test("should fail on unresolvable type in FragmentInputDefinition parameter") {
    val nodeId: String = "in"
    val invalidType    = "thisTypeDoesntExist"
    val paramName      = ParameterName("param1")

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              paramName,
              FragmentClazzRef(invalidType),
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = None
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed((error: FragmentParamClassLoadError) :: _, None, None) =>
      error.paramName shouldBe paramName
      error.refClazzName shouldBe invalidType
      error.nodeIds shouldBe Set(nodeId)
    }
  }

  test("shouldn't fail on valid validation expression") {
    val nodeId: String = "in"
    val paramName      = "param1"

    inside(
      validate(
        FragmentInputDefinition(
          nodeId,
          List(
            FragmentParameter(
              ParameterName(paramName),
              FragmentClazzRef[String],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = Some(
                ParameterValueCompileTimeValidation(
                  s"#${ValidationExpressionParameterValidator.variableName}.length() < 7".spel,
                  Some("some failed message")
                )
              )
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed(errors, None, None) =>
      errors shouldBe empty
    }
  }

  test("should fail on blank validation expression") {
    val blankExpression = "     "

    inside(
      validate(
        FragmentInputDefinition(
          "in",
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[String],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = Some(
                ParameterValueCompileTimeValidation(Expression.spel(blankExpression), Some("some failed message"))
              ),
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              InvalidValidationExpression(
                "Validation expression cannot be blank",
                "in",
                ParameterName("param1"),
                expr
              )
            ),
            None,
            None
          ) =>
        expr shouldBe blankExpression
    }
  }

  test("should fail on invalid validation expression") {
    val invalidReference = "#invalidReference"

    inside(
      validate(
        FragmentInputDefinition(
          "in",
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[String],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = Some(
                ParameterValueCompileTimeValidation(Expression.spel(invalidReference), Some("some failed message"))
              ),
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              InvalidValidationExpression(
                "Unresolved reference 'invalidReference'",
                "in",
                ParameterName("param1"),
                expr
              )
            ),
            None,
            None
          ) =>
        expr shouldBe invalidReference
    }
  }

  test("should fail on invalid-type validation expression") {
    val invalidExpression =
      s"#${ValidationExpressionParameterValidator.variableName} > 0" // invalid operation (comparing string with int)

    inside(
      validate(
        FragmentInputDefinition(
          "in",
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[String],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = Some(
                ParameterValueCompileTimeValidation(
                  invalidExpression.spel,
                  Some("some failed message")
                )
              )
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              InvalidValidationExpression(
                "Wrong part types",
                "in",
                ParameterName("param1"),
                expr
              )
            ),
            None,
            None
          ) =>
        expr shouldBe invalidExpression
    }
  }

  test("should fail on non-boolean-result-type validation expression") {
    val stringExpression = "'a' + 'b'"

    inside(
      validate(
        FragmentInputDefinition(
          "in",
          List(
            FragmentParameter(
              ParameterName("param1"),
              FragmentClazzRef[String],
              required = false,
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = Some(
                ParameterValueCompileTimeValidation(Expression.spel(stringExpression), Some("some failed message"))
              ),
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              InvalidValidationExpression(
                "Bad expression type, expected: Boolean, found: String(ab)",
                "in",
                ParameterName("param1"),
                expr
              )
            ),
            None,
            None
          ) =>
        expr shouldBe stringExpression
    }
  }

  test("should return error on invalid parameter name") {
    inside(
      validate(
        FragmentInputDefinition(
          "in",
          List(
            FragmentParameter(
              ParameterName("1"),
              FragmentClazzRef[String]
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              InvalidVariableName(
                "1",
                "in",
                Some(ParameterName("$param.1.$name"))
              )
            ),
            None,
            None
          ) =>
    }
  }

  test("should return error on duplicated parameter name") {
    val duplicatedParam = FragmentParameter(
      ParameterName("paramName"),
      FragmentClazzRef[String]
    )
    inside(
      validate(
        FragmentInputDefinition(
          "in",
          List(
            duplicatedParam,
            duplicatedParam
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              DuplicateFragmentInputParameter(ParameterName("paramName"), "in")
            ),
            None,
            None
          ) =>
    }
  }

  test("should not return specific errors based on parameter name when parameter names are duplicated") {
    inside(
      validate(
        FragmentInputDefinition(
          "in",
          List(
            FragmentParameter(
              name = ParameterName("paramName"),
              typ = FragmentClazzRef[String],
              initialValue = None,
              hintText = None,
              valueEditor = None,
              valueCompileTimeValidation = Some(
                ParameterValueCompileTimeValidation(
                  "invalidExpr".spel,
                  None
                )
              )
            ),
            FragmentParameter(
              ParameterName("paramName"),
              FragmentClazzRef[String],
            )
          ),
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(
              DuplicateFragmentInputParameter(ParameterName("paramName"), "in")
            ),
            None,
            None
          ) =>
    }
  }

  private def genericParameters = List(
    Parameter[String](ParameterName("par1"))
      .copy(
        editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
        defaultValue = Some("'realDefault'".spel),
        labelOpt = Some("Parameter 1")
      ),
    Parameter[Long](ParameterName("lazyPar1")).copy(isLazyParameter = true, defaultValue = Some("0".spel)),
    Parameter[Any](ParameterName("a")),
    Parameter[Any](ParameterName("b"))
  )

  private def validate(
      nodeData: NodeData,
      ctx: ValidationContext,
      branchCtxs: Map[String, ValidationContext] = Map.empty,
      outgoingEdges: List[OutgoingEdge] = Nil,
      fragmentDefinition: CanonicalProcess = defaultFragmentDef,
      aModelData: LocalModelData = modelData
  ): ValidationResponse = {
    val fragmentResolver = FragmentResolver(List(fragmentDefinition))
    val metaData         = MetaData("id", StreamMetaData())
    new NodeDataValidator(aModelData).validate(nodeData, ctx, branchCtxs, outgoingEdges, fragmentResolver)(
      JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
    )
  }

  private def par(name: String, expr: String): NodeParameter = NodeParameter(ParameterName(name), Expression.spel(expr))

}

object ExpressionsTestData extends TableDrivenPropertyChecks {

  val nullExpressions: TableFor1[String] = Table(
    "",
    "  ",
    "null",
    "true ? null : null"
  )

  val emptyOrBlankExpressions: TableFor1[String] = Table(
    "",
    "  "
  )

}
