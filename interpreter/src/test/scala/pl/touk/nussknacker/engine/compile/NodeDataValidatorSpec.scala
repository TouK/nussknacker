package pl.touk.nussknacker.engine.compile

import scala.jdk.CollectionConverters._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable}
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.fixedvaluespresets.{
  DefaultFixedValuesPresetProvider,
  FixedValuesPresetProvider,
  TestFixedValuesPresetProvider
}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, Unknown}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compile.nodecompilation.{NodeDataValidator, ValidationPerformed, ValidationResponse}
import pl.touk.nussknacker.engine.compile.validationHelpers._
import pl.touk.nussknacker.engine.graph.EdgeType.{FragmentOutput, NextSwitch}
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.fragment.FragmentRef
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.FragmentParameterInputMode.InputModeFixedList
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FragmentClazzRef,
  FragmentParameter,
  FragmentParameterFixedListPreset
}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.graph.variable.Field
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

class NodeDataValidatorSpec extends AnyFunSuite with Matchers with Inside {

  private val defaultConfig: Config = List("genericParametersSource", "genericParametersSink", "genericTransformer")
    .foldLeft(ConfigFactory.empty())((c, n) =>
      c.withValue(s"componentsUiConfig.$n.params.par1.defaultValue", fromAnyRef("'realDefault'"))
    )

  private val defaultFragmentId: String = "fragment1"

  private val fixedValuePresetId = "presetString"

  private val defaultFragmentDef: CanonicalProcess = CanonicalProcess(
    MetaData(defaultFragmentId, FragmentSpecificData()),
    List(
      FlatNode(
        FragmentInputDefinition(
          "in",
          List(
            FragmentParameter("param1", FragmentClazzRef[String]),
            FragmentParameterFixedListPreset(
              "paramPreset",
              FragmentClazzRef[String],
              required = false,
              None,
              None,
              inputMode = InputModeFixedList,
              fixedValueListPresetId = fixedValuePresetId,
              effectiveFixedValuesList = List.empty
            )
          )
        )
      ),
      FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'")))),
    )
  )

  private val defaultFragmentOutgoingEdges: List[OutgoingEdge] = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))

  def getModelData(aConfig: Config = defaultConfig): LocalModelData = {
    LocalModelData(
      aConfig,
      new EmptyProcessConfigCreator {
        override def customStreamTransformers(
            processObjectDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
          "genericJoin"        -> WithCategories(DynamicParameterJoinTransformer),
          "genericTransformer" -> WithCategories(GenericParametersTransformer),
          "genericTransformerUsingParameterValidator" -> WithCategories(
            GenericParametersTransformerUsingParameterValidator
          )
        )

        override def services(
            processObjectDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[Service]] = Map(
          "stringService"                               -> WithCategories(SimpleStringService),
          "genericParametersThrowingException"          -> WithCategories(GenericParametersThrowingException),
          "missingParamHandleGenericNodeTransformation" -> WithCategories(MissingParamHandleGenericNodeTransformation)
        )

        override def sourceFactories(
            processObjectDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[SourceFactory]] = Map(
          "genericParametersSource" -> WithCategories(new GenericParametersSource)
        )

        override def sinkFactories(
            processObjectDependencies: ProcessObjectDependencies
        ): Map[String, WithCategories[SinkFactory]] = Map(
          "genericParametersSink" -> WithCategories(GenericParametersSink)
        )
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
          List(par("par1", "'a,b'"), par("lazyPar1", "#aVar + 3"), par("a", "'a'"), par("b", "'dd'"))
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
    inside(validate(Filter("filter", "#a > 3"), ValidationContext(Map("a" -> Typed[String])))) {
      case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, None, _) =>
        error.message shouldBe "Wrong part types"
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
    val expectedError = WrongParameters(Set.empty, Set("param1"))(NodeId("fooNode"))
    inside(
      validate(
        node.Processor(
          "fooNode",
          ServiceRef(
            "missingParamHandleGenericNodeTransformation",
            List(
              par("param1", "'foo'")
            )
          )
        ),
        ValidationContext.empty
      )
    ) { case ValidationPerformed(`expectedError` :: Nil, parameters, _) =>
    }
  }

  test("should allow user variable") {
    inside(validate(Variable("var1", "specialVariable_2", "42L", None), ValidationContext())) {
      case ValidationPerformed(Nil, None, _) =>
    }
  }

  test("should validate variable definition") {
    inside(
      validate(Variable("var1", "var1", "doNotExist", None), ValidationContext(Map.empty))
    ) { case ValidationPerformed((error: ExpressionParserCompilationError) :: Nil, None, _) =>
      error.message shouldBe "Non reference 'doNotExist' occurred. Maybe you missed '#' in front of it?"
    }
  }

  test("should not allow to override output variable in variable definition") {
    inside(
      validate(Variable("var1", "var1", "42L", None), ValidationContext(localVariables = Map("var1" -> typing.Unknown)))
    ) { case ValidationPerformed(OverwrittenVariable("var1", "var1", _) :: Nil, None, _) =>
    }
  }

  test("should not allow to use special chars in variable name") {
    inside(validate(Variable("var1", "var@ 2", "42L", None), ValidationContext())) {
      case ValidationPerformed(InvalidVariableOutputName("var@ 2", "var1", _) :: Nil, None, _) =>
    }
  }

  test("should return expression type info for variable definition") {
    inside(validate(Variable("var1", "var1", "42L", None), ValidationContext(Map.empty))) {
      case ValidationPerformed(Nil, _, Some(expressionType)) =>
        expressionType.display shouldBe Typed.fromInstance(42L).display
    }
  }

  test("should validate variable builder definition") {
    inside(
      validate(VariableBuilder("var1", "var1", List(Field("field1", "doNotExist")), None), ValidationContext(Map.empty))
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

  test("should return inferred type for variable builder output") {
    inside(
      validate(
        VariableBuilder("var1", "var1", List(Field("field1", "42L"), Field("field2", "'some string'")), None),
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
        FragmentOutputDefinition("var1", "var1", List(Field("field1", "42L"), Field("field2", "'some string'")), None),
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
    val expectedMsg = s"Bad expression type, expected: String, found: ${Typed.fromInstance(145).display}"
    inside(
      validate(
        FragmentInput(
          "frInput",
          FragmentRef(
            "fragment1",
            List(Parameter("param1", "145"), Parameter("paramPreset", "'someOtherString'")),
            Map("out1" -> "test1")
          )
        ),
        ValidationContext.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(ExpressionParserCompilationError(expectedMsg, "frInput", Some("param1"), "145")),
            None,
            None
          ) =>
    }
  }

  test("should validate fragment parameters with validators -  - P1 as mandatory param with some actual value") {
    val defaultFragmentOutgoingEdges: List[OutgoingEdge] = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
    val fragmentId                                       = "fragmentInputId"
    val nodeToBeValidated =
      FragmentInput("nameOfTheNode", FragmentRef(fragmentId, List(Parameter("P1", "123")), Map("out1" -> "test1")))
    val fragmentDefinitionWithValidators: CanonicalProcess = CanonicalProcess(
      MetaData(fragmentId, FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("P1", FragmentClazzRef[Short])))),
        FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'")))),
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

  test("should validate fragment parameters with validators -  - P1 as mandatory param with missing actual value") {
    val fragmentId = "fragmentInputId"
    val nodeToBeValidated =
      FragmentInput("nameOfTheNode", FragmentRef(fragmentId, List(Parameter("P1", "")), Map("out1" -> "test1")))
    val fragmentDefinitionWithValidators: CanonicalProcess = CanonicalProcess(
      MetaData(fragmentId, FragmentSpecificData()),
      List(
        FlatNode(FragmentInputDefinition("in", List(FragmentParameter("P1", FragmentClazzRef[Short])))),
        FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'")))),
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
    ) should matchPattern {
      case ValidationPerformed(List(EmptyMandatoryParameter(_, _, "P1", fragmentId)), None, None) =>
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
          Parameter("P1", ""),
          Parameter("P2", ""),
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
              FragmentParameter("P1", FragmentClazzRef[Short]),
              FragmentParameter("P2", FragmentClazzRef[String])
            )
          )
        ),
        FlatNode(FragmentOutputDefinition("out", "out1", List(Field("strField", "'value'")))),
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
              EmptyMandatoryParameter(_, _, "P1", "nameOfTheNode"),
              EmptyMandatoryParameter(_, _, "P2", "nameOfTheNode")
            ),
            None,
            None
          ) =>
    }
  }

  test("should validate output parameters") {
    val incorrectVarName = "very bad var name"
    val varFieldName     = OutputVar.fragmentOutput("out1", "").fieldName
    val nodeId           = "frInput"
    inside(
      validate(
        FragmentInput(
          nodeId,
          FragmentRef(
            "fragment1",
            List(Parameter("param1", "'someValue'"), Parameter("paramPreset", "'someOtherString'")),
            Map("out1" -> incorrectVarName)
          )
        ),
        ValidationContext.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) {
      case ValidationPerformed(
            List(InvalidVariableOutputName(incorrectVarName, nodeId, Some(varFieldName))),
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
            List(Parameter("param1", "'someValue'"), Parameter("paramPreset", "'someOtherString'")),
            Map("out1" -> existingVar)
          )
        ),
        ValidationContext(Map(existingVar -> Typed[String])),
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1"))))
      )
    ) { case ValidationPerformed(List(OverwrittenVariable(existingVar, nodeId, Some(varFieldName))), None, None) =>
    }
  }

  test("should validate fragment output edges") {
    val nodeId = "frInput"
    val nodes  = Set("aa")
    inside(
      validate(
        FragmentInput(
          nodeId,
          FragmentRef(
            "fragment1",
            List(Parameter("param1", "'someValue'"), Parameter("paramPreset", "'someOtherString'")),
            Map("out1" -> "ok")
          )
        ),
        ValidationContext.empty
      )
    ) { case ValidationPerformed(List(FragmentOutputNotDefined("out1", nodes)), None, None) =>
    }

  }

  test("should validate switch") {
    inside(
      validate(
        Switch("switchId", Some("input"), Some("value1")),
        ValidationContext.empty,
        Map.empty,
        List(OutgoingEdge("caseTarget1", Some(NextSwitch("notExist"))))
      )
    ) {
      case ValidationPerformed(
            List(
              ExpressionParserCompilationError(
                "Non reference 'input' occurred. Maybe you missed '#' in front of it?",
                "switchId",
                Some("$expression"),
                "input"
              ),
              ExpressionParserCompilationError(
                "Non reference 'notExist' occurred. Maybe you missed '#' in front of it?",
                "switchId",
                Some("caseTarget1"),
                "notExist"
              )
            ),
            None,
            Some(Unknown)
          ) =>
    }
  }

  test("should validate missing fragment preset ids") {
    val nodeId: String = "in"
    val nodes1         = Set(nodeId)
    val nodes2         = Set(nodeId)
    inside(
      validate(
        FragmentInput(
          nodeId,
          FragmentRef(
            "fragment1",
            List(Parameter("param1", "'someValue'"), Parameter("paramPreset", "'someOtherString'")),
            Map("out1" -> "ok")
          )
        ),
        ValidationContext.empty,
        Map.empty,
        outgoingEdges = List(OutgoingEdge("any", Some(FragmentOutput("out1")))),
        fixedValuesPresetProvider = new DefaultFixedValuesPresetProvider(Map.empty)
      )
    ) {
      case ValidationPerformed(
            List(
              PresetIdNotFoundInProvidedPresets(
                fixedValuePresetId,
                nodes1
              ),
              RequireValueFromUndefinedFixedList(
                "paramPreset",
                nodes2
              )
            ),
            None,
            None
          ) =>
    }
  }

  private def genericParameters = List(
    definition
      .Parameter[String]("par1")
      .copy(
        editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
        defaultValue = Some("'realDefault'")
      ),
    definition.Parameter[Long]("lazyPar1").copy(isLazyParameter = true, defaultValue = Some("0")),
    definition.Parameter[Any]("a"),
    definition.Parameter[Any]("b")
  )

  private def validate(
      nodeData: NodeData,
      ctx: ValidationContext,
      branchCtxs: Map[String, ValidationContext] = Map.empty,
      outgoingEdges: List[OutgoingEdge] = Nil,
      fragmentDefinition: CanonicalProcess = defaultFragmentDef,
      aModelData: LocalModelData = modelData,
      fixedValuesPresetProvider: FixedValuesPresetProvider = TestFixedValuesPresetProvider
  ): ValidationResponse = {
    val fragmentResolver = FragmentResolver(List(fragmentDefinition))
    new NodeDataValidator(aModelData, fragmentResolver).validate(
      nodeData,
      ctx,
      branchCtxs,
      outgoingEdges,
      fixedValuesPresetProvider
    )(
      MetaData("id", StreamMetaData())
    )
  }

  private def par(name: String, expr: String): Parameter = Parameter(name, Expression.spel(expr))

}
