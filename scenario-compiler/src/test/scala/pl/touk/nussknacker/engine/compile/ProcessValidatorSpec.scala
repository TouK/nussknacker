package pl.touk.nussknacker.engine.compile

import cats.data._
import cats.data.Validated.{Invalid, Valid}
import cats.instances.string._
import org.scalatest.{Inside, OptionValues}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedSingleParameter}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonicalgraph.canonicalnode.FlatNode
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler
import pl.touk.nussknacker.engine.definition.component.{
  ComponentDefinitionWithImplementation,
  CustomComponentSpecificData,
  NodeCompilationDependencies
}
import pl.touk.nussknacker.engine.definition.component.ComponentImplementationInvoker.ComponentImplementationSpecificInvocationContext
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.IndexBasedTextRange
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.expression.Expression.Language
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.service.{EagerServiceWithStaticParameters, EnricherContextTransformation}
import pl.touk.nussknacker.engine.util.typing.TypingUtils
import pl.touk.nussknacker.engine.variables.MetaVariables

import java.util.Collections
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends AnyFunSuite with Matchers with Inside with OptionValues {

  private val nonEndingOneInputComponent = CustomComponentSpecificData(canHaveManyInputs = false, canBeEnding = false)

  private val baseDefinitionBuilder = ModelDefinitionBuilder.empty
    .withGlobalVariable("processHelper", ProcessHelper)
    .withService("sampleEnricher", Some(Typed[SimpleRecord]))
    .withService(
      "sampleEnricherWithPlainRecord",
      Some(Typed.record(List("someString" -> Typed[String], "someInt" -> Typed[Integer])))
    )
    .withService("withParamsService", Some(Typed[SimpleRecord]), Parameter[String](ParameterName("par1")))
    .withUnboundedStreamSource("source", Some(Typed[SimpleRecord]))
    .withUnboundedStreamSource("sourceWithUnknown", Some(Unknown))
    .withUnboundedStreamSource("sourceWithParam", Some(Typed[SimpleRecord]), Parameter[Any](ParameterName("param")))
    .withSink("sink")
    .withSink("sinkWithLazyParam", Parameter[String](ParameterName("lazyString")).copy(isLazyParameter = true))
    .withCustom("customTransformer", Some(Typed[SimpleRecord]), nonEndingOneInputComponent)
    .withCustom(
      "withParamsTransformer",
      Some(Typed[SimpleRecord]),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("par1"))
    )
    .withCustom(
      "manyParams",
      Some(Typed[SimpleRecord]),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("par1")).copy(isLazyParameter = true),
      Parameter[String](ParameterName("par2")),
      Parameter[String](ParameterName("par3")).copy(isLazyParameter = true),
      Parameter[String](ParameterName("par4"))
    )
    .withCustom(
      "withManyParameters",
      Some(Typed[SimpleRecord]),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("lazyString")).copy(isLazyParameter = true),
      Parameter[Integer](ParameterName("lazyInt")).copy(isLazyParameter = true),
      Parameter[Long](ParameterName("long")).copy(validators = List(MinimalNumberValidator(0)))
    )
    .withCustom(
      "withoutReturnType",
      None,
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("par1"))
    )
    .withCustom(
      "withMandatoryParams",
      Some(Unknown),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("mandatoryParam"))
    )
    .withCustom(
      "withNotBlankParams",
      Some(Unknown),
      nonEndingOneInputComponent,
      NotBlankParameter(ParameterName("notBlankParam"), Typed[String])
    )
    .withCustom(
      "withNullableLiteralIntegerParam",
      Some(Unknown),
      nonEndingOneInputComponent,
      Parameter[Integer](ParameterName("nullableLiteralIntegerParam")).copy(validators =
        List(CompileTimeEvaluableValueValidator)
      )
    )
    .withCustom(
      "withRegExpParam",
      Some(Unknown),
      nonEndingOneInputComponent,
      Parameter[Integer](ParameterName("regExpParam")).copy(validators = List(CompileTimeEvaluableValueValidator))
    )
    .withCustom(
      "withJsonParam",
      Some(Unknown),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("jsonParam")).copy(validators = List(JsonValidator))
    )
    .withCustom(
      "withCustomValidatorParam",
      Some(Unknown),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("param"))
        .copy(validators = List(CustomParameterValidatorDelegate("test_custom_validator")))
    )
    .withCustom(
      "withAdditionalVariable",
      Some(Unknown),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("param")).copy(
        additionalVariables = Map("additional" -> AdditionalVariableProvidedInRuntime[Int]),
        isLazyParameter = true
      )
    )
    .withCustom(
      "withVariablesToHide",
      Some(Unknown),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("param")).copy(variablesToHide = Set("input"), isLazyParameter = true)
    )

  private val baseDefinition = baseDefinitionBuilder.build

  private val definitionWithTypedSourceBuilder =
    baseDefinitionBuilder.withUnboundedStreamSource("typed-source", Some(Typed[SimpleRecord]))

  private val definitionWithTypedSource = definitionWithTypedSourceBuilder.build

  private val definitionWithTypedSourceAndTransformNode =
    definitionWithTypedSourceBuilder.withCustom(
      "custom",
      Some(Typed[AnotherSimpleRecord]),
      nonEndingOneInputComponent,
      Parameter[String](ParameterName("par1"))
    )

  test("enable method execution for Unknown") {
    val baseDefinitionCopy = baseDefinition.copy(
      expressionConfig = baseDefinition.expressionConfig.copy(methodExecutionForUnknownAllowed = true)
    )

    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .filter("filter1", "#input.imaginary".spel)
      .filter("filter2", "#input.imaginaryMethod()".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinitionCopy)

    compilationResult.result should matchPattern { case Valid(_) =>
    }
  }

  test("disable method execution for Unknown") {

    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .filter("filter1", "#input.imaginary".spel)
      .filter("filter2", "#input.imaginaryMethod()".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Property access on Unknown is not allowed",
                NodeId("filter1"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              _
            )
          ) =>
    }

  }

  test("Validation of Type Reference using allowed class and method") {

    val filterPredicateExpression = "T(java.math.BigInteger).valueOf(1L) == 1"

    val testProcess =
      ScenarioBuilder
        .streaming("TypeReferenceClassValidationSuccess")
        .source("source1", "source")
        .filter("filter1", filterPredicateExpression.spel)
        .buildSimpleVariable("result-id1", "result", "#input".spel)
        .emptySink("end-id1", "sink")

    val compilationResult = validate(testProcess, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }

  }

  test("Validation of Type Reference using accessible class with not validated method") {

    val filterPredicateExpression = "T(String).copyValueOf('test')"

    val testProcess =
      ScenarioBuilder
        .streaming("TypeReferenceClassValidationSuccess")
        .source("source1", "source")
        .filter("filter1", filterPredicateExpression.spel)
        .buildSimpleVariable("result-id1", "result", "#input".spel)
        .emptySink("end-id1", "sink")

    val compilationResult = validate(testProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unknown method 'copyValueOf' in String",
                NodeId("filter1"),
                Some(DefaultExpressionIdParamName),
                "T(String).copyValueOf('test')",
                _
              ),
              _
            )
          ) =>
    }

  }

  test("Validation of Type Reference using inaccessible class, failure scenario") {

    val filterPredicateExpression = "T(System).exit()"

    val testProcess =
      ScenarioBuilder
        .streaming("TypeReferenceClassValidationFailure")
        .source("source1", "source")
        .filter("filter1", filterPredicateExpression.spel)
        .buildSimpleVariable("result-id1", "result", "#input".spel)
        .emptySink("end-id1", "sink")

    val compilationResult = validate(testProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "class java.lang.System is not allowed to be passed as TypeReference",
                NodeId("filter1"),
                Some(DefaultExpressionIdParamName),
                "T(System).exit()",
                _
              ),
              _
            )
          ) =>
    }

  }

  test("Valid dynamic property access when available") {
    val baseDefinitionCopy =
      baseDefinition.copy(expressionConfig = baseDefinition.expressionConfig.copy(dynamicPropertyAccessAllowed = true))

    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "#input['plainValue'] == 1".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinitionCopy)

    compilationResult.result should matchPattern { case Valid(_) =>
    }
  }

  test("Invalid dynamic property access when disabled") {
    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "#input['plainValue'] == 1".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Dynamic property access is not allowed",
                NodeId("filter1"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              _
            )
          ) =>
    }
  }

  test("valid TypedUnion while indexing") {
    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "{{\"\"}, {0}}[0][0] == 0 ".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }
  }

  test("validated with success") {
    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "#input.plainValueOpt + 1 > 1".spel)
      .filter("filter2", "#input.plainValueOpt.abs + 1 > 1".spel)
      .filter("filter3", "#input.intAsAny + 1 > 1".spel)
      .processor("sampleProcessor1", "sampleEnricher")
      .enricher("sampleProcessor2", "out", "sampleEnricher")
      .buildVariable(
        "bv1",
        "vars",
        "v1"             -> "42".spel,
        "recordVariable" -> "{ Field1: 'Field1Value', Field2: 'Field2Value', Field3: #input.plainValue }".spel,
        "spelVariable"   -> "(#input.list.?[plainValue == 5]).![plainValue].contains(5)".spel
      )
      .emptySink("id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }

    compilationResult.variablesInNodes.keySet shouldEqual Set(
      "id1",
      "filter1",
      "filter2",
      "filter3",
      "sampleProcessor1",
      "sampleProcessor2",
      "bv1",
      "id2"
    )
    compilationResult.variablesInNodes.get("id1").value shouldEqual Map(
      "meta"          -> MetaVariables.typingResult(correctProcess.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
    compilationResult.variablesInNodes.get("filter1").value shouldEqual Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(correctProcess.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
    compilationResult.variablesInNodes.get("filter2").value shouldEqual Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(correctProcess.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
    compilationResult.variablesInNodes.get("filter3").value shouldEqual Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(correctProcess.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
    compilationResult.variablesInNodes.get("sampleProcessor1").value shouldEqual Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(correctProcess.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
    compilationResult.variablesInNodes.get("sampleProcessor2").value shouldEqual Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(correctProcess.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
    compilationResult.variablesInNodes.get("bv1").value shouldEqual Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(correctProcess.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "out"           -> Typed[SimpleRecord]
    )
    val varsType =
      compilationResult.variablesInNodes.get("id2").value.get("vars").value.asInstanceOf[TypedObjectTypingResult]
    varsType.fields.get("v1").value shouldEqual Typed.fromInstance(42)
    varsType.fields.get("recordVariable").value shouldEqual Typed.record(
      ListMap(
        "Field1" -> Typed.fromInstance("Field1Value"),
        "Field2" -> Typed.fromInstance("Field2Value"),
        "Field3" -> Typed[java.math.BigDecimal]
      )
    )
    varsType.fields.get("spelVariable").value shouldEqual Typed[Boolean]
  }

  test("allow global variables in source definition") {
    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithParam", "param" -> "#processHelper".spel)
      .buildSimpleVariable("result-id2", "result", "#input".spel)
      .emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }
  }

  test("should handle all cases of scenario id validation") {
    forAll(IdValidationTestData.scenarioIdErrorCases) {
      (scenarioId: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          val scenario = ScenarioBuilder
            .streaming(scenarioId)
            .source("sourceId", "source")
            .emptySink("sinkId", "sink")
          validate(scenario, baseDefinition).result match {
            case Valid(_)   => expectedErrors shouldBe empty
            case Invalid(e) => e.toList shouldBe expectedErrors
          }
        }
    }
  }

  test("should handle all cases of fragment id validation") {
    forAll(IdValidationTestData.fragmentIdErrorCases) {
      (fragmentId: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          val fragment = ScenarioBuilder
            .fragmentWithInputNodeId(fragmentId, "sourceId")
            .emptySink("sinkId", "sink")
          validate(fragment, baseDefinition, isFragment = true).result match {
            case Valid(_)   => expectedErrors shouldBe empty
            case Invalid(e) => e.toList shouldBe expectedErrors

          }
        }
    }
  }

  test("should handle all cases node id validation independently from node name") {
    forAll(IdValidationTestData.nodeIdErrorCases) { (nodeId: String, expectedErrors: List[ProcessCompilationError]) =>
      {
        val scenario = ScenarioBuilder
          .streaming("scenarioId")
          .source(nodeId, "source")
          .emptySink("sinkId", "sink")
          .mapAllNodes(_.map {
            case FlatNode(source: Source) if source.ref.typ == "source" =>
              FlatNode(source.copy(name = NodeName("validNodeName")))
            case node =>
              node
          })
        validate(scenario, baseDefinition).result match {
          case Valid(_)   => expectedErrors shouldBe empty
          case Invalid(e) => e.toList shouldBe expectedErrors
        }
      }
    }
  }

  test("should handle all cases node name validation independently from node id") {
    forAll(IdValidationTestData.nodeNameErrorCases) {
      (nodeName: String, expectedErrors: List[ProcessCompilationError]) =>
        {
          val scenario = ScenarioBuilder
            .streaming("scenarioId")
            .source("validNodeId", "source")
            .emptySink("sinkId", "sink")
            .mapAllNodes(_.map {
              case FlatNode(source: Source) if source.id == NodeId("validNodeId") =>
                FlatNode(source.copy(name = NodeName(nodeName)))
              case node =>
                node
            })
          validate(scenario, baseDefinition).result match {
            case Valid(_)   => expectedErrors shouldBe empty
            case Invalid(e) => e.toList shouldBe expectedErrors

          }
        }
    }
  }

  test("find duplicated ids") {
    val duplicatedId = "id1"
    val processWithDuplicatedIds =
      ScenarioBuilder.streaming("process1").source(duplicatedId, "source").emptySink(duplicatedId, "sink")
    validate(processWithDuplicatedIds, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  test("find duplicated ids in switch") {
    val duplicatedId = "id1"
    val processWithDuplicatedIds =
      ScenarioBuilder
        .streaming("process1")
        .source("source", "source")
        .switch(
          "switch",
          "''".spel,
          "var",
          Case("'1'".spel, GraphBuilder.emptySink(duplicatedId, "sink")),
          Case("'2'".spel, GraphBuilder.emptySink(duplicatedId, "sink"))
        )
    validate(processWithDuplicatedIds, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  test("find duplicated node names") {
    val sameName = NodeName("same")
    val process = CanonicalProcess(
      MetaData("process1", StreamMetaData()),
      List(
        FlatNode(Source(NodeId("1"), sameName, SourceRef("source", Nil))),
        FlatNode(Sink(NodeId("2"), sameName, SinkRef("sink", Nil))),
      )
    )

    validate(process, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeNames(_, _), _)) =>
    }
  }

  test("find expression parse error") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .buildSimpleVariable("result-id2", "result", "wtf!!!".spel)
        .emptySink("end-id2", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError(_, _, _, _, _), _)) =>
    }
  }

  test("find mandatory expressions for mandatory parameters") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withMandatoryParams", "mandatoryParam" -> "".spel)
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(EmptyMandatoryParameter(_, _, ParameterName("mandatoryParam"), NodeId("customNodeId")), _)
          ) =>
    }
  }

  test("find blank expressions for notBlank parameter") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId1", "event", "withNotBlankParams", "notBlankParam" -> "''".spel)
        .customNode("customNodeId2", "event2", "withNotBlankParams", "notBlankParam" -> "'   '".spel)
        .customNode("customNodeId3", "event3", "withNotBlankParams", "notBlankParam" -> " '' ".spel)
        .customNode("customNodeId4", "event4", "withNotBlankParams", "notBlankParam" -> " '  ' ".spel)
        .customNode("customNodeId5", "event5", "withNotBlankParams", "notBlankParam" -> "'test'".spel)
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              BlankParameter(_, _, ParameterName("notBlankParam"), NodeId("customNodeId1")),
              List(
                BlankParameter(_, _, ParameterName("notBlankParam"), NodeId("customNodeId2")),
                BlankParameter(_, _, ParameterName("notBlankParam"), NodeId("customNodeId3")),
                BlankParameter(_, _, ParameterName("notBlankParam"), NodeId("customNodeId4"))
              )
            )
          ) =>
    }
  }

  test("valid for Literal Integer param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode(
          "customNodeId",
          "event",
          "withNullableLiteralIntegerParam",
          "nullableLiteralIntegerParam" -> "12".spel
        )
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("valid for Nullable Literal Integer param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode(
          "customNodeId",
          "event",
          "withNullableLiteralIntegerParam",
          "nullableLiteralIntegerParam" -> "".spel
        )
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("invalid for Nullable Literal Integer param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode(
          "customNodeId",
          "event",
          "withNullableLiteralIntegerParam",
          "nullableLiteralIntegerParam" -> "as".spel
        )
        .customNode(
          "customNodeId2",
          "event2",
          "withNullableLiteralIntegerParam",
          "nullableLiteralIntegerParam" -> "1.23".spel
        )
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                _,
                NodeId("customNodeId"),
                Some(ParameterName("nullableLiteralIntegerParam")),
                "as",
                _
              ),
              List(
                ExpressionParserCompilationError(
                  _,
                  NodeId("customNodeId2"),
                  Some(ParameterName("nullableLiteralIntegerParam")),
                  "1.23",
                  _
                )
              )
            )
          ) =>
    }
  }

  test("mismatch for Literal Number param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withRegExpParam", "regExpParam" -> "as".spel)
        .customNode("customNodeId2", "event", "withRegExpParam", "regExpParam" -> "1.23".spel)
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(_, NodeId("customNodeId"), Some(ParameterName("regExpParam")), "as", _),
              _
            )
          ) =>
    }
  }

  test("valid for json param") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withJsonParam", "jsonParam" -> "'{\"example\": \"json\"}'".spel)
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("invalid for json param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event1", "withJsonParam", "jsonParam" -> "'{'".spel)
        .customNode("customNodeId2", "event2", "withJsonParam", "jsonParam" -> "'{\"}'".spel)
        .customNode(
          "customNodeId3",
          "event3",
          "withJsonParam",
          "jsonParam" -> "'{\"invalid\" : \"json\" : \"0\"}'".spel
        )
        .customNode("customNodeId4", "event4", "withJsonParam", "jsonParam" -> "'{\"invalid\" : [\"json\"}'".spel)
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              JsonRequiredParameter(_, _, ParameterName("jsonParam"), NodeId("customNodeId")),
              List(
                JsonRequiredParameter(_, _, ParameterName("jsonParam"), NodeId("customNodeId2")),
                JsonRequiredParameter(_, _, ParameterName("jsonParam"), NodeId("customNodeId3")),
                JsonRequiredParameter(_, _, ParameterName("jsonParam"), NodeId("customNodeId4"))
              )
            )
          ) =>
    }
  }

  test("find missing service error") {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "customTransformer")
        .processor("id2", missingServiceId, "foo" -> "'bar'".spel)
        .emptySink("sink", "sink")

    val compilationResult = validate(processWithRefToMissingService, baseDefinition)
    compilationResult.result should matchPattern { case Invalid(NonEmptyList(MissingService(_, _), _)) =>
    }
    compilationResult.variablesInNodes("sink") shouldBe Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(processWithRefToMissingService.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "event"         -> Typed[SimpleRecord]
    )

    val validDefinition =
      baseDefinitionBuilder.withService(missingServiceId, Parameter[String](ParameterName("foo"))).build
    validate(processWithRefToMissingService, validDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("find missing source") {
    val serviceId = "serviceId"
    val processWithRefToMissingService =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .filter("filter", "#input != null".spel)
        .buildSimpleVariable("simple", "simpleVar", "'simple'".spel)
        .processorEnd("id2", serviceId, "foo" -> "'bar'".spel)

    val definition = ModelDefinitionBuilder.empty.withService(serviceId, Parameter[String](ParameterName("foo"))).build
    val compilationResult = validate(processWithRefToMissingService, definition)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(MissingSourceFactory("source", NodeId("id1")), Nil)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map(
      "input"     -> Unknown,
      "meta"      -> MetaVariables.typingResult(processWithRefToMissingService.metaData),
      "simpleVar" -> Typed.fromInstance("simple")
    )

  }

  test("find missing custom node") {
    val processWithRefToMissingService =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "out", "notExisting", "dummy" -> "input".spel)
        .emptySink("id2", "sink")

    validate(processWithRefToMissingService, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(MissingCustomNodeExecutor("notExisting", NodeId("custom")), Nil)) =>
    }
  }

  test("find usage of unresolved plain variables") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildVariable("bv1", "doesExist", "v1" -> "42".spel)
      .filter("sampleFilter", "#doesExist.v1 + #doesNotExist1 + #doesNotExist2 > 10".spel)
      .emptySink("id2", "sink")
    validate(process, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unresolved reference 'doesNotExist1'",
                NodeId("sampleFilter"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              List(
                ExpressionParserCompilationError(
                  "Unresolved reference 'doesNotExist2'",
                  NodeId("sampleFilter"),
                  Some(DefaultExpressionIdParamName),
                  _,
                  _
                )
              )
            )
          ) =>
    }
  }

  test("find usage of non references") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValue > 10".spel)
      .filter("sampleFilter2", "input.plainValue > 10".spel)
      .emptySink("id2", "sink")
    validate(process, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Non reference 'input' occurred. Maybe you missed '#' in front of it?",
                NodeId("sampleFilter2"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              _
            )
          ) =>
    }
  }

  test("find usage of fields that does not exist in object") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .filter("sampleFilter1", "#input.value1.value2 > 10".spel)
      .filter("sampleFilter2", "#input.value1.value3 > 10".spel)
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "There is no property 'value3' in type: AnotherSimpleRecord",
                NodeId("sampleFilter2"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              _
            )
          ) =>
    }
  }

  test("find not existing variables after custom node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'".spel)
      .filter("sampleFilter2", "#input.value1.value3 > 10".spel)
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode.build

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "There is no property 'value3' in type: AnotherSimpleRecord",
                NodeId("sampleFilter2"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              _
            )
          ) =>
    }
  }

  test("find not existing variables after split") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .split(
        "split1",
        GraphBuilder.emptySink("id2", "sink"),
        GraphBuilder.filter("sampleFilter2", "#input.value1.value3 > 10".spel).emptySink("id3", "sink")
      )

    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode.build

    val compilationResult = validate(process, definitionWithCustomNode)
    compilationResult.result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "There is no property 'value3' in type: AnotherSimpleRecord",
                NodeId("sampleFilter2"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              _
            )
          ) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
    compilationResult.variablesInNodes("id3") shouldBe Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass)
    )
  }

  test("validate custom node return type") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .customNodeNoOutput("noOutput", "withoutReturnType", "par1" -> "'1'".spel)
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'".spel)
      .filter("sampleFilter1", "#out1.value2 > 0".spel)
      .filter("sampleFilter2", "#out1.terefere".spel)
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode.build

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "There is no property 'terefere' in type: AnotherSimpleRecord",
                NodeId("sampleFilter2"),
                Some(DefaultExpressionIdParamName),
                "#out1.terefere",
                _
              ),
              _
            )
          ) =>
    }
  }

  test("doesn't allow unknown vars in custom node params") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .customNode("cNode1", "out1", "custom", "par1" -> "#strangeVar".spel)
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode.build

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unresolved reference 'strangeVar'",
                NodeId("cNode1"),
                Some(ParameterName("par1")),
                "#strangeVar",
                _
              ),
              _
            )
          ) =>
    }
  }

  test("should allow mock expression matching enricher return type") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .enricherWithMockExpression(
        "enricher1",
        "out",
        "sampleEnricherWithPlainRecord",
        """{someString: "abc", someInt: 1234}""".spel
      )
      .emptySink("id2", "sink")

    inside(validate(process, definitionWithTypedSource).result) { case Valid(_) =>
    }
  }

  test("should not allow mock expression not matching enricher return type - spel expression") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .enricherWithMockExpression("enricher1", "out", "sampleEnricherWithPlainRecord", """{someString: "abc"}""".spel)
      .emptySink("id2", "sink")

    inside(validate(process, definitionWithTypedSource).result) {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Bad expression type, expected: Record{someInt: Integer, someString: String}, found: Record{someString: String(abc)}",
                NodeId("enricher1"),
                Some(NodeCompiler.MockExpressionParameterName),
                _,
                _
              ),
              Nil
            )
          ) =>
    }
  }

  test("should not allow mock expression not matching enricher return type - json template expression") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .enricherWithMockExpression(
        "enricher1",
        "out",
        "sampleEnricherWithPlainRecord",
        Expression(Language.JsonTemplate, """{"someString": "abc"}""")
      )
      .emptySink("id2", "sink")

    inside(validate(process, definitionWithTypedSource).result) {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Bad expression type, expected: Record{someInt: Integer, someString: String}, found: Record{someString: String}",
                NodeId("enricher1"),
                Some(NodeCompiler.MockExpressionParameterName),
                _,
                _
              ),
              Nil
            )
          ) =>
    }
  }

  test("find usage of fields that does not exist in option object") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .filter("sampleFilter1", "#input.plainValueOpt.terefere > 10".spel)
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "There is no property 'terefere' in type: BigDecimal",
                NodeId("sampleFilter1"),
                Some(DefaultExpressionIdParamName),
                _,
                _
              ),
              _
            )
          ) =>
    }
  }

  test("return field/property names in errors") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .split(
        "split",
        GraphBuilder.processorEnd("p1", "withParamsService", "par1" -> "#terefere".spel),
        GraphBuilder.customNode("c1", "output", "withParamsTransformer", "par1" -> "{".spel).emptySink("id2", "sink"),
        GraphBuilder
          .buildVariable("v1", "output", "par1" -> "#terefere22".spel)
          .emptySink("id3", "sink")
      )

    // sortBy is for having defined order
    validate(process, definitionWithTypedSource).result.leftMap(_.sortBy(_.toString)) should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unexpectedly ran out of input",
                NodeId("c1"),
                Some(ParameterName("par1")),
                _,
                _
              ),
              List(
                ExpressionParserCompilationError(
                  "Unresolved reference 'terefere'",
                  NodeId("p1"),
                  Some(ParameterName("par1")),
                  _,
                  _
                ),
                ExpressionParserCompilationError(
                  "Unresolved reference 'terefere22'",
                  NodeId("v1"),
                  Some(ParameterName("$fields-0-$value")),
                  _,
                  _
                )
              )
            )
          ) =>
    }
  }

  test("not allow to overwrite variable by variable node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .buildSimpleVariable("var1", "var1", "''".spel)
      .buildSimpleVariable("var1overwrite", "var1", "''".spel)
      .emptySink("id2", "sink")
    val compilationResult = validate(process, definitionWithTypedSource)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", NodeId("var1overwrite"), _), _)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "var1"          -> Typed.fromInstance("")
    )
  }

  test("not allow to overwrite variable by switch node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .buildSimpleVariable("var1", "var1", "''".spel)
      .switch("var1overwrite", "''".spel, "var1", GraphBuilder.emptySink("id2", "sink"))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", NodeId("var1overwrite"), _), _)) =>
    }
  }

  test("not allow to overwrite variable by enricher node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .buildSimpleVariable("var1", "var1", "''".spel)
      .enricher("var1overwrite", "var1", "sampleEnricher")
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", NodeId("var1overwrite"), _), _)) =>
    }
  }

  test("not allow to overwrite variable by variable builder") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .buildSimpleVariable("var1", "var1", "''".spel)
      .buildVariable("var1overwrite", "var1", "a" -> "''".spel)
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", NodeId("var1overwrite"), _), _)) =>
    }
  }

  test("validate variable builder fields usage") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .buildVariable("valr", "var1", "a" -> "''".spel, "b" -> "11".spel)
      .buildSimpleVariable("working", "var2", "#var1.b > 10".spel)
      .buildSimpleVariable("notWorking", "var3", "#var1.a > 10".spel)
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Wrong part types",
                NodeId("notWorking"),
                Some(DefaultExpressionIdParamName),
                "#var1.a > 10",
                _
              ),
              _
            )
          ) =>
    }
  }

  test("not allow to overwrite variable by custom node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .buildSimpleVariable("var1", "var1", "''".spel)
      .customNode("var1overwrite", "var1", "custom", "par1" -> "''".spel)
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSourceAndTransformNode.build).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", NodeId("var1overwrite"), _), _)) =>
    }
  }

  test("allow different vars in branches") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .switch(
        "switch",
        "''".spel,
        "var2",
        GraphBuilder.buildSimpleVariable("var3", "var3", "''".spel).emptySink("id2", "sink"),
        Case(
          "true".spel,
          GraphBuilder.buildSimpleVariable("var3b", "var3", "#var2.length()".spel).emptySink("id3", "sink")
        )
      )

    val compilationResult = validate(process, definitionWithTypedSource)
    compilationResult.result should matchPattern { case Valid(_) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "var2"          -> Typed.fromInstance(""),
      "var3"          -> Typed.fromInstance("")
    )
    compilationResult.variablesInNodes("id3") shouldBe Map(
      "input"         -> Typed[SimpleRecord],
      "meta"          -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "var2"          -> Typed.fromInstance(""),
      "var3"          -> Typed[Int]
    )
  }

  test("not allow to use vars from different branches") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .switch(
        "switch",
        "''".spel,
        "var2",
        GraphBuilder.buildSimpleVariable("var3", "var3", "''".spel).emptySink("id2", "sink"),
        Case("false".spel, GraphBuilder.buildSimpleVariable("id3", "result", "#var3".spel).emptySink("end3", "sink"))
      )

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unresolved reference 'var3'",
                NodeId("id3"),
                Some(DefaultExpressionIdParamName),
                "#var3",
                _
              ),
              _
            )
          ) =>
    }
  }

  test("validates case expressions in switch") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "typed-source")
      .switch("switch", "''".spel, "var2", Case("#notExist".spel, GraphBuilder.emptySink("end1", "sink")))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unresolved reference 'notExist'",
                NodeId("switch"),
                Some(ParameterName("end1")),
                "#notExist",
                _
              ),
              _
            )
          ) =>
    }
  }

  test("allow customNode outputVar when no return type in definition - it will be reported only as a warning in logs") {
    val processWithOutputWhenNoOutputNeeded =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "varName", "withoutReturnType", "par1" -> "'1'".spel)
        .buildSimpleVariable("result-id2", "result", "''".spel)
        .emptySink("end-id2", "sink")

    validate(processWithOutputWhenNoOutputNeeded, baseDefinition).result shouldBe Symbol("valid")
  }

  test("require customNode outputVar when return type in definition") {
    val processWithInvalidExpresssion =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNodeNoOutput("custom", "customTransformer")
        .buildSimpleVariable("result-id2", "result", "''".spel)
        .emptySink("end-id2", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(vars, NodeId("custom")), Nil))
          if vars == Set(ParameterName("OutputVariable")) =>
    }
  }

  test("should validate process with parameters in different order than in definition") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode(
          "custom",
          "outVar",
          "withManyParameters",
          "long"       -> "123123123133L".spel,
          "lazyString" -> "'44'".spel,
          "lazyInt"    -> "43".spel
        )
        .buildSimpleVariable("result-id2", "result", "''".spel)
        .emptySink("end-id2", "sink")

    validate(process, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("should propagate error from source creation") {
    val base = baseDefinition
    val failingDefinition = base
      .copy(
        base.components.copy(
          base.components.components.map {
            case component if component.componentType == ComponentType.Source =>
              component.withImplementationInvoker(
                (
                    _: Params,
                    _: NodeCompilationDependencies,
                    _: Option[ComponentImplementationSpecificInvocationContext]
                ) => {
                  throw new RuntimeException("You passed incorrect parameter, cannot proceed")
                }
              )
            case other => other
          }
        )
      )

    val processWithInvalidExpresssion =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .buildSimpleVariable("result-id2", "result", "''".spel)
        .emptySink("end-id2", "sink")

    validate(processWithInvalidExpresssion, failingDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              CannotCreateObjectError("You passed incorrect parameter, cannot proceed", NodeId("id1"), _, _),
              Nil
            )
          ) =>
    }

  }

  test("should be able to derive type from ServiceReturningType") {
    val base = baseDefinition
    val withServiceRef = base.withComponent(
      ComponentDefinitionWithImplementation.withEmptyConfig("returningTypeService", ServiceReturningTypeSample)
    )

    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .enricher(
          "serviceDef",
          "defined",
          "returningTypeService",
          "definition" -> "{param1: 'String', param2: 'Integer'}".spel,
          "inRealTime" -> "#input.toString()".spel
        )
        .emptySink("id2", "sink")

    val result = validate(process, withServiceRef)
    result.result should matchPattern { case Valid(_) =>
    }
    result.variablesInNodes("id2")("defined") shouldBe Typed.genericTypeClass[java.util.List[_]](
      List(Typed.record(ListMap("param1" -> Typed[String], "param2" -> Typed[Integer])))
    )

  }

  test("should override parameter definition from WithExplicitMethodToInvoke by definition from ServiceReturningType") {
    val base = baseDefinition
    val withServiceRef = base.withComponent(
      ComponentDefinitionWithImplementation.withEmptyConfig(
        "returningTypeService",
        ServiceReturningTypeWithExplicitMethodSample
      )
    )

    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .enricher(
          "serviceDef",
          "defined",
          "returningTypeService",
          "definition" -> "{param1: 'String', param2: 'Integer'}".spel,
          "inRealTime" -> "#input.toString()".spel
        )
        .emptySink("id2", "sink")

    val result = validate(process, withServiceRef)
    result.result should matchPattern { case Valid(_) =>
    }
    result.variablesInNodes("id2")("defined") shouldBe Typed.genericTypeClass[java.util.List[_]](
      List(Typed.record(ListMap("param1" -> Typed[String], "param2" -> Typed[Integer])))
    )
  }

  test("should be able to run custom validation using ServiceReturningType") {
    val base = baseDefinition
    val withServiceRef = base.withComponent(
      ComponentDefinitionWithImplementation.withEmptyConfig("withCustomValidation", ServiceWithCustomValidation)
    )

    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .enricher("service-1", "output1", "withCustomValidation", "age" -> "12".spel, "fields" -> "{:}".spel)
        .enricher(
          "service-2",
          "output2",
          "withCustomValidation",
          "age"    -> "30".spel,
          "fields" -> "{invalid: 'yes'}".spel
        )
        .buildSimpleVariable("result-id2", "result", "''".spel)
        .emptySink("end-id2", "sink")

    val result = validate(process, withServiceRef)

    result.result shouldBe Invalid(
      NonEmptyList.of(
        CustomNodeError(NodeId("service-1"), "Too young", Some(ParameterName("age"))),
        CustomNodeError(NodeId("service-2"), "Service is invalid", None),
      )
    )
  }

  test("should detect duplicated parameters in node") {
    val processWithLocalVarInEagerParam =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode(
          "custom",
          "outVar",
          "withParamsTransformer",
          "par1" -> "#input.toString()".spel,
          "par1" -> "''".spel,
          "par2" -> "#input.toString()".spel,
          "par2" -> "''".spel
        )
        .emptySink("id2", "sink")

    validate(processWithLocalVarInEagerParam, baseDefinition).result shouldBe
      Invalid(
        NonEmptyList.of(DuplicatedParameters(Set(ParameterName("par1"), ParameterName("par2")), NodeId("custom")))
      )
  }

  test("not allows local variables in eager custom node parameter") {
    val processWithLocalVarInEagerParam =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "outVar", "withParamsTransformer", "par1" -> "#input.toString()".spel)
        .emptySink("id2", "sink")

    validate(processWithLocalVarInEagerParam, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unresolved reference 'input'",
                NodeId("custom"),
                Some(ParameterName("par1")),
                "#input.toString()",
                _
              ),
              _
            )
          ) =>
    }
  }

  test("correctly detects lazy params") {
    val processWithLocalVarInEagerParam =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode(
          "custom",
          "outVar",
          "manyParams",
          "par1" -> "#input.toString()".spel,
          "par2" -> "''".spel,
          "par3" -> "#input.toString()".spel,
          "par4" -> "''".spel
        )
        .emptySink("id2", "sink")

    validate(processWithLocalVarInEagerParam, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("allows using local variables for sink with lazy parameters") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .emptySink("sinkWithLazyParam", "sinkWithLazyParam", "lazyString" -> "#input.toString()".spel)

    validate(process, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("allow using process properties via meta.properties") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .additionalFields(properties = Map("property1" -> "value1"))
        .source("id1", "source")
        .buildSimpleVariable("var1", "var1", "#meta.properties.property1".spel)
        .emptySink("sink", "sink")

    validate(process, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("hide meta variable using feature flag") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .buildSimpleVariable("var1", "var1", "#meta.processName".spel)
        .emptySink("sink", "sink")

    validate(process, baseDefinition).result should matchPattern { case Valid(_) =>
    }

    validate(
      process,
      baseDefinition.copy(expressionConfig = baseDefinition.expressionConfig.copy(hideMetaVariable = true))
    ).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Unresolved reference 'meta'", _, _, _, _), _)) =>
    }
  }

  test("extract expression typing info") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", "source")
        .filter("filter", "true".spel)
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map.empty,
      "filter" -> Map(
        DefaultExpressionIdParamName.value -> SpelExpressionTypingInfo(
          Map(IndexBasedTextRange(0, 4) -> Typed.fromInstance(true)),
          Typed.fromInstance(true)
        )
      ),
      "sink" -> Map.empty
    )
  }

  test("extract expression typing info from source parameters") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", "sourceWithParam", "param" -> "123".spel)
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map(
        "param" -> SpelExpressionTypingInfo(
          Map(IndexBasedTextRange(0, 3) -> Typed.fromInstance(123)),
          Typed.fromInstance(123)
        )
      ),
      "sink" -> Map.empty
    )
  }

  test("extract expression typing info from sink parameters") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", "source")
        .emptySink("sink", "sinkWithLazyParam", "lazyString" -> "'123'".spel)

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map.empty,
      "sink" -> Map(
        "lazyString" -> SpelExpressionTypingInfo(
          Map(IndexBasedTextRange(0, 5) -> Typed.fromInstance("123")),
          Typed.fromInstance("123")
        )
      )
    )
  }

  test("extract expression typing info from custom node parameters") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", "source")
        .customNode("customNode", "out", "withParamsTransformer", "par1" -> "'123'".spel)
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern { case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map.empty,
      "customNode" -> Map(
        "par1" -> SpelExpressionTypingInfo(
          Map(IndexBasedTextRange(0, 5) -> Typed.fromInstance("123")),
          Typed.fromInstance("123")
        )
      ),
      "sink" -> Map.empty
    )
  }

  test("validation of method returning future values") {
    val process = ScenarioBuilder
      .streaming("proc1")
      .source("id", "source")
      .buildSimpleVariable("sampleVar", "var", "#processHelper.futureValue".spel)
      .buildSimpleVariable("sampleVar2", "var2", "#processHelper.identity(#var)".spel)
      .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Mismatch parameter types. Found: identity(Future[String]). Required: identity(String)",
                _,
                _,
                _,
                _
              ),
              Nil
            )
          ) =>
    }
  }

  test("validate with custom validator") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withCustomValidatorParam", "param" -> "'Aaaaa'".spel)
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("validate negatively with custom validator") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withCustomValidatorParam", "param" -> "'Aaaaa'".spel)
        .customNode("customNodeId2", "event1", "withCustomValidatorParam", "param" -> "'Baaaa'".spel)
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              CustomParameterValidationError(_, _, ParameterName("param"), NodeId("customNodeId2")),
              Nil
            )
          ) =>
    }
  }

  test("use additional variables in expressions") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withAdditionalVariable", "param" -> "#additional.toString".spel)
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern { case Valid(_) =>
    }
  }

  test("hide variables in expression") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withVariablesToHide", "param" -> "#input.toString".spel)
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Invalid(
            NonEmptyList(
              ExpressionParserCompilationError(
                "Unresolved reference 'input'",
                NodeId("customNodeId"),
                Some(ParameterName("param")),
                _,
                _
              ),
              _
            )
          ) =>
    }
  }

  test("validates fragment variables") {
    val usedVarName = "sampleVar"

    def scenario(outputName: String) = ScenarioBuilder
      .streaming("scenario1")
      .source("id1", "source")
      .buildSimpleVariable("var1", usedVarName, "''".spel)
      .fragmentOneOut("sample", "frag1", "output1", outputName)
      .emptySink("emptySink", "sink")

    val fragment = ScenarioBuilder
      .fragment("frag1")
      .fragmentOutput("out", "output1", "field1" -> "''".spel)
    val resolver = FragmentResolver(List(fragment))

    val withNonUsed = resolver.resolve(scenario("nonUsedVar")).andThen(validate(_, baseDefinition).result)
    withNonUsed shouldBe Symbol("valid")

    val withUsed       = resolver.resolve(scenario(usedVarName)).andThen(validate(_, baseDefinition).result)
    val errorFieldName = OutputVar.fragmentOutput("output1", "").fieldName

    withUsed should matchPattern {
      case Invalid(
            NonEmptyList(
              OverwrittenVariable(`usedVarName`, NodeId("sample-out"), Some(ParameterName(`errorFieldName`))),
              Nil
            )
          ) =>
    }
  }

  test("allow same node name in scenario and inside used fragment") {
    val scenario = ScenarioBuilder
      .streaming("scenario1")
      .source("source", "source")
      .filter("filter", "true".spel)
      .fragmentOneOut("fragRef", "frag1", "output", "fragmentResult")
      .emptySink("sink", "sink")

    val fragment = ScenarioBuilder
      .fragment("frag1")
      .filter("filter", "true".spel)
      .fragmentOutput("out", "output")

    val resolver = FragmentResolver(List(fragment))

    resolver.resolve(scenario).andThen(validate(_, baseDefinition).result) shouldBe Symbol("valid")
  }

  // This tests an artificial canonical process which cannot be created through conversion from ScenarioGraph because of
  // skipping loose nodes and empty main branch. We added it to show that the canonical errors folding algorithm works
  // correctly.
  test("should return merged graph structure errors of different types") {
    val variableName1 = "variable1"
    val variableName2 = "variable2"
    val sourceName1   = "source1"
    val sourceName2   = "source2"
    val metaData      = MetaData("scenario1", StreamMetaData())
    val scenarioWith =
      CanonicalProcess(
        metaData,
        List(),
        List(
          List(FlatNode(Variable(NodeId(variableName1), NodeName(variableName1), "varName", "'str'".spel))),
          List(FlatNode(Variable(NodeId(variableName2), NodeName(variableName2), "varName", "'str'".spel))),
          List(FlatNode(Source(NodeId(sourceName1), NodeName(sourceName1), SourceRef("source", List())))),
          List(FlatNode(Source(NodeId(sourceName2), NodeName(sourceName2), SourceRef("source", List())))),
        )
      )

    inside(validate(scenarioWith, baseDefinition).result) { case Invalid(errors) =>
      errors.toList should contain theSameElementsAs
        List(
          InvalidRootNode(Set(NodeId(variableName1), NodeId(variableName2))),
          InvalidTailOfBranch(Set(NodeId(sourceName1), NodeId(sourceName2))),
          EmptyProcess
        )
    }
  }

  private def validate(
      process: CanonicalProcess,
      definitions: ModelDefinition,
      isFragment: Boolean = false
  ): CompilationResult[Unit] = {
    val jobData: JobData = JobData(process.metaData, ProcessVersion.empty.copy(processName = process.metaData.name))
    implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
      new ScenarioCompilationDependencies(jobData, EngineScenarioCompilationDependencies.empty)
    ProcessValidator
      .default(
        ModelDefinitionWithClasses(definitions),
        new SimpleDictRegistry(Map.empty),
        CustomProcessValidatorLoader.emptyCustomProcessValidator
      )
      .validate(process, isFragment)
  }

  case class SimpleRecord(
      value1: AnotherSimpleRecord,
      plainValue: java.math.BigDecimal,
      plainValueOpt: Option[java.math.BigDecimal],
      intAsAny: Any,
      list: java.util.List[SimpleRecord]
  ) {
    private val privateValue = "priv"

    def invoke1: Future[AnotherSimpleRecord] = ???

    def someMethod(a: Int): Int = ???
  }

  case class AnotherSimpleRecord(value2: Long)

  class SampleEnricher extends Service {

    def invoke()(implicit ec: ExecutionContext): Future[SimpleRecord] =
      Future.successful(
        SimpleRecord(
          AnotherSimpleRecord(1),
          new java.math.BigDecimal(1),
          Option(new java.math.BigDecimal(2)),
          1,
          Collections.emptyList[SimpleRecord]
        )
      )

  }

  object ProcessHelper {
    def add(a: Int, b: Int): Int = a + b

    def identity(nullableVal: String): String = nullableVal

    def futureValue: Future[String] = Future.successful("123")
  }

  object ServiceReturningTypeSample extends EagerService {

    @MethodToInvoke
    def invoke(
        @ParamName("definition") definition: java.util.Map[String, _],
        @ParamName("inRealTime") inRealTime: LazyParameter[String],
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): ContextTransformation =
      EnricherContextTransformation(
        variableName,
        Typed.genericTypeClass[java.util.List[_]](List(TypingUtils.typeMapDefinition(definition))),
        new ServiceInvoker {

          override def invoke(context: Context)(
              implicit ec: ExecutionContext,
              collector: InvocationCollectors.ServiceInvocationCollector,
              componentUseContext: ComponentUseContext,
          ): Future[Any] = Future.successful(null)

        }
      )

  }

  object ServiceReturningTypeWithExplicitMethodSample extends EagerServiceWithStaticParameters {

    override val hasOutput = true

    override def returnType(
        validationContext: ValidationContext,
        parameters: Map[ParameterName, DefinedSingleParameter]
    )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, TypingResult] = {
      Valid(
        parameters
          .get(ParameterName("definition"))
          .collect { case DefinedEagerParameter(value: java.util.Map[String @unchecked, _], _) =>
            TypingUtils.typeMapDefinition(value)
          }
          .map(param => Typed.genericTypeClass[java.util.List[_]](List(param)))
          .getOrElse(Unknown)
      )
    }

    override def createServiceInvoker(
        eagerParameters: Map[ParameterName, Any],
        lazyParameters: Map[ParameterName, LazyParameter[AnyRef]],
        typingResult: TypingResult,
        metaData: MetaData
    ): ServiceInvoker = new ServiceInvoker {

      override def invoke(context: Context)(
          implicit ec: ExecutionContext,
          collector: InvocationCollectors.ServiceInvocationCollector,
          componentUseContext: ComponentUseContext,
      ): Future[Any] = Future.successful(null)

    }

    // @ParamName("definition") definition: java.util.Map[String, _], @ParamName("inRealTime") inRealTime: String
    override def parameters: List[Parameter] = List(
      Parameter.optional(
        name = ParameterName("definition"),
        typ = Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown)),
      ),
      Parameter
        .optional(
          name = ParameterName("inRealTime"),
          typ = Typed.typedClass(classOf[String]),
        )
        .copy(isLazyParameter = true)
    )

  }

  object ServiceWithCustomValidation extends EagerService {

    @MethodToInvoke
    def invoke(
        @ParamName("age") age: Int,
        @ParamName("fields") fields: LazyParameter[java.util.Map[String, String]],
        @OutputVariableName variableName: String
    )(implicit nodeId: NodeId): ContextTransformation = {
      def returnType: ValidatedNel[ProcessCompilationError, TypingResult] = {
        if (age < 18) {
          Validated.invalidNel(CustomNodeError("Too young", Some(ParameterName("age"))))
        } else {
          fields.returnType match {
            case TypedObjectTypingResult(fields, _, _) if fields.contains("invalid") =>
              Validated.invalidNel(CustomNodeError("Service is invalid", None))
            case _ => Valid(Typed.typedClass[String])
          }
        }
      }

      EnricherContextTransformation(
        variableName,
        returnType,
        new ServiceInvoker {
          override def invoke(context: Context)(
              implicit ec: ExecutionContext,
              collector: InvocationCollectors.ServiceInvocationCollector,
              componentUseContext: ComponentUseContext,
          ): Future[Any] =
            Future.successful(
              s"name: ${fields.evaluate(context).get("name")}, age: $age"
            )
        }
      )
    }

  }

}

class StartingWithACustomValidator extends CustomParameterValidator {
  override def name: String = "test_custom_validator"

  import cats.data.Validated.{invalid, valid}

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None                                 => valid(())
      case Some(null)                           => valid(())
      case Some(s: String) if s.startsWith("A") => valid(())
      case _ =>
        invalid(
          CustomParameterValidationError(
            message = s"Value $value does not starts with 'A'",
            description = "Value does not starts with 'A'",
            paramName = paramName,
            nodeId = nodeId
          )
        )
    }
  }

}
