package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.instances.string._
import org.scalatest.Inside
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedEagerParameter, DefinedSingleParameter}
import pl.touk.nussknacker.engine.api.context.{ContextTransformation, PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ComponentUseCase, LanguageConfiguration, WithCategories}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.build.{ScenarioBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef, StandardObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessObjectDefinitionExtractor}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId._
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.engine.util.service.{EagerServiceWithStaticParameters, EnricherContextTransformation}
import pl.touk.nussknacker.engine.util.typing.TypingUtils
import pl.touk.nussknacker.engine.variables.MetaVariables

import java.util.Collections
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends AnyFunSuite with Matchers with Inside {


  private val emptyQueryNamesData = CustomTransformerAdditionalData(Set(), false, false)

  private val baseDefinition = ProcessDefinition[ObjectDefinition](
    Map("sampleEnricher" -> ObjectDefinition(List.empty, Typed[SimpleRecord]), "withParamsService" -> ObjectDefinition(List(Parameter[String]("par1")),
      Typed[SimpleRecord])),
    Map("source" -> ObjectDefinition(List.empty, Typed[SimpleRecord]),
        "sourceWithUnknown" -> ObjectDefinition(List.empty, Unknown),
        "sourceWithParam" -> ObjectDefinition(List(Parameter[Any]("param")), Typed[SimpleRecord]),
        "typedMapSource" -> ObjectDefinition(List(Parameter[TypedObjectDefinition]("type")), Typed[TypedMap])
    ),
    Map("sink" -> ObjectDefinition.noParam,
      "sinkWithLazyParam" -> ObjectDefinition.withParams(List(Parameter[String]("lazyString").copy(isLazyParameter = true)))),

    Map("customTransformer" -> (ObjectDefinition(List.empty, Typed[SimpleRecord]), emptyQueryNamesData),
      "withParamsTransformer" -> (ObjectDefinition(List(Parameter[String]("par1")), Typed[SimpleRecord]), emptyQueryNamesData),
      "manyParams" -> (ObjectDefinition(List(
                Parameter[String]("par1").copy(isLazyParameter = true),
                Parameter[String]("par2"),
                Parameter[String]("par3").copy(isLazyParameter = true),
                Parameter[String]("par4")), Typed[SimpleRecord]), emptyQueryNamesData),
      "withManyParameters" -> (ObjectDefinition(List(
        Parameter[String]("lazyString").copy(isLazyParameter = true), Parameter[Integer]("lazyInt").copy(isLazyParameter = true),
        Parameter[Long]("long").copy(validators = List(MinimalNumberValidator(0))))
      , Typed[SimpleRecord]), emptyQueryNamesData),
      "withoutReturnType" -> (ObjectDefinition(List(Parameter[String]("par1")), Typed[Void]), emptyQueryNamesData),
      "withMandatoryParams" -> (ObjectDefinition.withParams(List(Parameter[String]("mandatoryParam"))), emptyQueryNamesData),
      "withNotBlankParams" -> (ObjectDefinition.withParams(List(NotBlankParameter("notBlankParam", Typed.typedClass(classOf[String])))), emptyQueryNamesData),
      "withNullableLiteralIntegerParam" -> (ObjectDefinition.withParams(List(
        Parameter[Integer]("nullableLiteralIntegerParam").copy(validators = List(LiteralParameterValidator.integerValidator))
      )), emptyQueryNamesData),
      "withRegExpParam" -> (ObjectDefinition.withParams(List(
        Parameter[Integer]("regExpParam").copy(validators = List(LiteralParameterValidator.numberValidator))
      )), emptyQueryNamesData),
      "withJsonParam" -> (ObjectDefinition.withParams(List(
        Parameter[String]("jsonParam").copy(validators = List(JsonValidator))
      )), emptyQueryNamesData),
      "withCustomValidatorParam" -> (ObjectDefinition.withParams(List(
        Parameter[String]("param").copy(validators = List(CustomParameterValidatorDelegate("test_custom_validator")))
      )), emptyQueryNamesData),
      "withAdditionalVariable" -> (ObjectDefinition.withParams(List(
        Parameter[String]("param").copy(additionalVariables = Map("additional" -> AdditionalVariableProvidedInRuntime[Int]), isLazyParameter = true)
      )), emptyQueryNamesData),
      "withVariablesToHide" -> (ObjectDefinition.withParams(List(
        Parameter[String]("param").copy(variablesToHide = Set("input"), isLazyParameter = true)
      )), emptyQueryNamesData)
    ),
    Map.empty,
    ExpressionDefinition(
      Map("processHelper" -> ObjectDefinition(List(), Typed(ProcessHelper.getClass))), List.empty, List.empty,
      LanguageConfiguration.default, optimizeCompilation = false, strictTypeChecking = true, dictionaries = Map.empty, hideMetaVariable = false,
      strictMethodsChecking = true, staticMethodInvocationsChecking = true, methodExecutionForUnknownAllowed = false, dynamicPropertyAccessAllowed = false,
      spelExpressionExcludeList = SpelExpressionExcludeList.default, customConversionsProviders = List.empty),
    ClassExtractionSettings.Default
  )

  test("enable method execution for Unknown") {
    val baseDefinitionCopy = baseDefinition.copy(
      expressionConfig = baseDefinition.expressionConfig.copy(
        methodExecutionForUnknownAllowed = true))

    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .filter("filter1", "#input.imaginary")
      .filter("filter2", "#input.imaginaryMethod()")
     .buildSimpleVariable("result-id2", "result", "#input").emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinitionCopy)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }
  }

  test("disable method execution for Unknown") {

    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "sourceWithUnknown")
      .filter("filter1", "#input.imaginary")
      .filter("filter2", "#input.imaginaryMethod()")
     .buildSimpleVariable("result-id2", "result", "#input").emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Property access on Unknown is not allowed", "filter1", Some(DefaultExpressionId), _), _)) =>
    }

  }

  test("Validation of Type Reference using allowed class and method") {

    val filterPredicateExpression = "T(java.math.BigInteger).valueOf(1L) == 1"

    val testProcess =
      ScenarioBuilder
        .streaming("TypeReferenceClassValidationSuccess")
        .source("source1", "source")
        .filter("filter1", filterPredicateExpression)
       .buildSimpleVariable("result-id1", "result", "#input").emptySink("end-id1", "sink")

    val compilationResult = validate(testProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

  }

  test("Validation of Type Reference using accessible class with not validated method") {

    val filterPredicateExpression = "T(String).copyValueOf('test')"

    val testProcess =
      ScenarioBuilder
        .streaming("TypeReferenceClassValidationSuccess")
        .source("source1", "source")
        .filter("filter1", filterPredicateExpression)
       .buildSimpleVariable("result-id1", "result", "#input").emptySink("end-id1", "sink")

    val compilationResult = validate(testProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Unknown method 'copyValueOf' in String", "filter1", Some(DefaultExpressionId), "T(String).copyValueOf('test')"), _)) =>
    }

  }

  test("Validation of Type Reference using inaccessible class, failure scenario") {

    val filterPredicateExpression = "T(System).exit()"

    val testProcess =
      ScenarioBuilder
        .streaming("TypeReferenceClassValidationFailure")
        .source("source1", "source")
        .filter("filter1", filterPredicateExpression)
       .buildSimpleVariable("result-id1", "result", "#input").emptySink("end-id1", "sink")

    val compilationResult = validate(testProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("class java.lang.System is not allowed to be passed as TypeReference", "filter1", Some(DefaultExpressionId), "T(System).exit()"), _)) =>
    }

  }

  test("Valid dynamic property access when available") {
    val baseDefinitionCopy = baseDefinition.copy(
      expressionConfig = baseDefinition.expressionConfig.copy(
        dynamicPropertyAccessAllowed = true))

    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "#input['plainValue'] == 1")
     .buildSimpleVariable("result-id2", "result", "#input").emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinitionCopy)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }
  }


  test("Invalid dynamic property access when disabled") {
    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "#input['plainValue'] == 1")
     .buildSimpleVariable("result-id2", "result", "#input").emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Dynamic property access is not allowed", "filter1", Some(DefaultExpressionId), _), _)) =>
    }
  }

  test("valid TypedUnion while indexing") {
    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "{{\"\"}, {0}}[0][0] == 0 ")
     .buildSimpleVariable("result-id2", "result", "#input").emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }
  }

  test("validated with success") {
    val correctProcess = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("filter1", "#input.plainValueOpt + 1 > 1")
      .filter("filter2", "#input.plainValueOpt.abs + 1 > 1")
      .filter("filter3", "#input.intAsAny + 1 > 1")
      .processor("sampleProcessor1", "sampleEnricher")
      .enricher("sampleProcessor2", "out", "sampleEnricher")
      .buildVariable("bv1", "vars", "v1" -> "42",
        "mapVariable" -> "{ Field1: 'Field1Value', Field2: 'Field2Value', Field3: #input.plainValue }",
        "spelVariable" -> "(#input.list.?[plainValue == 5]).![plainValue].contains(5)"
      )
     .emptySink("id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.variablesInNodes shouldBe Map(
      "id1" -> Map("meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "filter1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "filter2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "filter3" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "sampleProcessor1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "sampleProcessor2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "bv1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass), "out" -> Typed[SimpleRecord]),
      "id2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass), "out" -> Typed[SimpleRecord],
        "vars" -> TypedObjectTypingResult(ListMap(
          "v1" -> Typed.fromInstance(42),
          "mapVariable" -> TypedObjectTypingResult(ListMap(
            "Field1" -> Typed.fromInstance("Field1Value"),
            "Field2" -> Typed.fromInstance("Field2Value"),
            "Field3" -> Typed[BigDecimal]
          )),
          "spelVariable" ->  Typed[Boolean]
        ))
      )
    )
  }

  test("allow global variables in source definition") {
    val correctProcess = ScenarioBuilder
          .streaming("process1")
          .source("id1", "sourceWithParam", "param" -> "#processHelper")
         .buildSimpleVariable("result-id2", "result", "#input").emptySink("end-id2", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }
  }

  test("find duplicated ids") {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = ScenarioBuilder.streaming("process1").source(duplicatedId, "source").emptySink(duplicatedId, "sink")
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
        .switch("switch", "''", "var",
          Case("'1'", GraphBuilder.emptySink(duplicatedId, "sink")),
          Case("'2'", GraphBuilder.emptySink(duplicatedId, "sink"))
        )
    validate(processWithDuplicatedIds, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  test("find expression parse error") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
       .buildSimpleVariable("result-id2", "result", "wtf!!!").emptySink("end-id2", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError(_, _, _, _), _)) =>
    }
  }

  test ("find mandatory expressions for mandatory parameters") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withMandatoryParams", "mandatoryParam" -> "")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(EmptyMandatoryParameter(_, _, "mandatoryParam", "customNodeId"), _)) =>
    }
  }

  test ("find blank expressions for notBlank parameter") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId1", "event", "withNotBlankParams", "notBlankParam" -> "''")
        .customNode("customNodeId2", "event2", "withNotBlankParams", "notBlankParam" -> "'   '")
        .customNode("customNodeId3", "event3", "withNotBlankParams", "notBlankParam" -> " '' ")
        .customNode("customNodeId4", "event4", "withNotBlankParams", "notBlankParam" -> " '  ' ")
        .customNode("customNodeId5", "event5", "withNotBlankParams", "notBlankParam" -> "'test'")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
        BlankParameter(_, _, "notBlankParam", "customNodeId1"),
        List(
          BlankParameter(_, _, "notBlankParam", "customNodeId2"),
          BlankParameter(_, _, "notBlankParam", "customNodeId3"),
          BlankParameter(_, _, "notBlankParam", "customNodeId4")
        )
      )) =>
    }
  }

  test ("valid for Literal Integer param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withNullableLiteralIntegerParam", "nullableLiteralIntegerParam" -> "12")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("valid for Nullable Literal Integer param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withNullableLiteralIntegerParam", "nullableLiteralIntegerParam" -> "")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("invalid for Nullable Literal Integer param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withNullableLiteralIntegerParam", "nullableLiteralIntegerParam" -> "as")
        .customNode("customNodeId2", "event2", "withNullableLiteralIntegerParam", "nullableLiteralIntegerParam" -> "1.23")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
        InvalidIntegerLiteralParameter(_, _, "nullableLiteralIntegerParam", "customNodeId"),
        List(
          InvalidIntegerLiteralParameter(_, _, "nullableLiteralIntegerParam", "customNodeId2")
        )
      )) =>
    }
  }

  test ("mismatch for Literal Number param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withRegExpParam", "regExpParam" -> "as")
        .customNode("customNodeId2", "event", "withRegExpParam", "regExpParam" -> "1.23")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
        MismatchParameter(_, _, "regExpParam", "customNodeId"), _
      )) =>
    }
  }

  test ("valid for json param") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withJsonParam", "jsonParam" -> "'{\"example\": \"json\"}'")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("invalid for json param") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event1", "withJsonParam", "jsonParam" -> "'{'")
        .customNode("customNodeId2", "event2", "withJsonParam", "jsonParam" -> "'{\"}'")
        .customNode("customNodeId3", "event3", "withJsonParam", "jsonParam" -> "'{\"invalid\" : \"json\" : \"0\"}'")
        .customNode("customNodeId4", "event4", "withJsonParam", "jsonParam" -> "'{\"invalid\" : [\"json\"}'")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
        JsonRequiredParameter(_, _, "jsonParam", "customNodeId"),
        List(
          JsonRequiredParameter(_, _, "jsonParam", "customNodeId2"),
          JsonRequiredParameter(_, _, "jsonParam", "customNodeId3"),
          JsonRequiredParameter(_, _, "jsonParam", "customNodeId4")
        )
      )) =>
    }
  }

  test("find missing service error") {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "customTransformer")
        .processor("id2", missingServiceId, "foo" -> "'bar'")
        .emptySink("sink", "sink")

    val compilationResult = validate(processWithRefToMissingService, baseDefinition)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(MissingService(_, _), _)) =>
    }
    compilationResult.variablesInNodes("sink") shouldBe Map(
      "input" -> Typed[SimpleRecord],
      "meta" -> MetaVariables.typingResult(processWithRefToMissingService.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "event" -> Typed[SimpleRecord]
    )

    val validDefinition = baseDefinition.withService(missingServiceId, Parameter[String]("foo"))
    validate(processWithRefToMissingService, validDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("find redundant service parameters") {
    val serviceId = "serviceId"
    val definition = baseDefinition.withService(serviceId)

    val redundantServiceParameter = "foo"

    val processWithInvalidServiceInvocation =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .processorEnd("id2", serviceId, redundantServiceParameter -> "'bar'")

    validate(processWithInvalidServiceInvocation, definition).result should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(_, _), _)) =>
    }
  }

  test("find missing source") {
    val serviceId = "serviceId"
    val processWithRefToMissingService =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .filter("filter", "#input != null")
        .buildSimpleVariable("simple", "simpleVar", "'simple'")
        .processorEnd("id2", serviceId, "foo" -> "'bar'")

    val definition = ProcessDefinitionBuilder.empty.withService(serviceId, Parameter[String]("foo"))
    val compilationResult = validate(processWithRefToMissingService, definition)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(MissingSourceFactory("source", "id1"), Nil)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map(
      "input" -> Unknown,
      "meta" -> MetaVariables.typingResult(processWithRefToMissingService.metaData),
      "simpleVar" -> Typed.fromInstance("simple")
    )

  }

  test("find missing custom node") {
    val processWithRefToMissingService =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "out", "notExisting", "dummy" -> "input")
        .emptySink("id2", "sink")

    validate(processWithRefToMissingService, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(MissingCustomNodeExecutor("notExisting", "custom"), Nil)) =>
    }
  }

  test("find usage of unresolved plain variables") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildVariable("bv1", "doesExist", "v1" -> "42")
      .filter("sampleFilter", "#doesExist.v1 + #doesNotExist1 + #doesNotExist2 > 10")
      .emptySink("id2", "sink")
    validate(process, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParserCompilationError("Unresolved reference 'doesNotExist1'", "sampleFilter", Some(DefaultExpressionId), _),
      List(ExpressionParserCompilationError("Unresolved reference 'doesNotExist2'", "sampleFilter", Some(DefaultExpressionId), _)))) =>
    }
  }

  test("find usage of non references") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValue > 10")
      .filter("sampleFilter2", "input.plainValue > 10")
      .emptySink("id2", "sink")
    validate(process, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Non reference 'input' occurred. Maybe you missed '#' in front of it?", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
  }

  test("find usage of fields that does not exist in object") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("sampleFilter1", "#input.value1.value2 > 10")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("There is no property 'value3' in type: AnotherSimpleRecord", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
  }


  test("find not existing variables after custom node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("There is no property 'value3' in type: AnotherSimpleRecord", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
  }

  test("find not existing variables after split") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .split("split1",
        GraphBuilder.emptySink("id2", "sink"),
        GraphBuilder.filter("sampleFilter2", "#input.value1.value3 > 10").emptySink("id3", "sink")
      )

    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    val compilationResult = validate(process, definitionWithCustomNode)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("There is no property 'value3' in type: AnotherSimpleRecord", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ProcessHelper.getClass))
    compilationResult.variablesInNodes("id3") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ProcessHelper.getClass))
  }

  test("validate custom node return type") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .customNodeNoOutput("noOutput", "withoutReturnType", "par1" -> "'1'")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter1", "#out1.value2 > 0")
      .filter("sampleFilter2", "#out1.terefere")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("There is no property 'terefere' in type: AnotherSimpleRecord",
      "sampleFilter2", Some(DefaultExpressionId), "#out1.terefere"), _)) =>
    }
  }

  test("doesn't allow unknown vars in custom node params") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "#strangeVar")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Unresolved reference 'strangeVar'", "cNode1", Some("par1"), "#strangeVar"), _)) =>
    }
  }

  test("validate service params") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .enricher("enricher1", "out", "withParamsService")
      .emptySink("id2", "sink")

    inside (validate(process, definitionWithTypedSource).result) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "enricher1"), _)) => missingParam shouldBe Set("par1")
    }
  }

  test("find usage of fields that does not exist in option object") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValueOpt.terefere > 10")
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("There is no property 'terefere' in type: BigDecimal", "sampleFilter1", Some(DefaultExpressionId), _), _)) =>
    }
  }

  test("return field/property names in errors") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .split("split",
        GraphBuilder.processorEnd("p1", "withParamsService", "par1" -> "#terefere"),
        GraphBuilder.customNode("c1", "output", "withParamsTransformer", "par1" -> "{").emptySink("id2", "sink"),
        GraphBuilder.buildVariable("v1", "output", "par1" -> "#terefere22")
          .emptySink("id3", "sink")
      )

    //sortBy is for having defined order
    validate(process, definitionWithTypedSource).result.leftMap(_.sortBy(_.toString)) should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParserCompilationError("Expression [{] @0: EL1044E: Unexpectedly ran out of input", "c1", Some("par1"), _),
      List(
      ExpressionParserCompilationError("Unresolved reference 'terefere'", "p1", Some("par1"), _),
      ExpressionParserCompilationError("Unresolved reference 'terefere22'", "v1", Some("par1"), _))
      )) =>
    }
  }

  test("not allow to overwrite variable by variable node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .buildSimpleVariable("var1overwrite", "var1", "''")
      .emptySink("id2", "sink")
    val compilationResult = validate(process, definitionWithTypedSource)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map(
      "input" -> Typed[SimpleRecord],
      "meta" -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "var1" -> Typed.fromInstance("")
    )
  }

  test("not allow to overwrite variable by switch node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .switch("var1overwrite", "''", "var1", GraphBuilder.emptySink("id2", "sink"))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("not allow to overwrite variable by enricher node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .enricher("var1overwrite", "var1", "sampleEnricher")
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("not allow to overwrite variable by variable builder") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .buildVariable("var1overwrite", "var1", "a" -> "''")
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("validate variable builder fields usage") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildVariable("valr", "var1", "a" -> "''", "b" -> "11")
      .buildSimpleVariable("working", "var2", "#var1.b > 10")
      .buildSimpleVariable("notWorking", "var3", "#var1.a > 10")
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Wrong part types", "notWorking", Some(DefaultExpressionId), "#var1.a > 10"), _)) =>
    }
  }

  test("not allow to overwrite variable by custom node") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .customNode("var1overwrite", "var1", "custom", "par1" -> "''")
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSourceAndTransformNode).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("allow different vars in branches") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .switch("switch", "''", "var2",
        GraphBuilder.buildSimpleVariable("var3", "var3", "''").emptySink("id2", "sink"),
         Case("true", GraphBuilder.buildSimpleVariable("var3b", "var3", "#var2.length()").emptySink("id3", "sink")))

    val compilationResult = validate(process, definitionWithTypedSource)
    compilationResult.result should matchPattern {
      case Valid(_) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map(
      "input" -> Typed[SimpleRecord],
      "meta" -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "var2" -> Typed.fromInstance(""),
      "var3" -> Typed.fromInstance(""))
    compilationResult.variablesInNodes("id3") shouldBe Map(
      "input" -> Typed[SimpleRecord],
      "meta" -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "var2" -> Typed.fromInstance(""),
      "var3" -> Typed[Int]
    )
  }

  test("not allow to use vars from different branches") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .switch("switch", "''", "var2",
        GraphBuilder.buildSimpleVariable("var3", "var3", "''").emptySink("id2", "sink"),
         Case("false", GraphBuilder.buildSimpleVariable("id3", "result", "#var3").emptySink("end3", "sink")))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Unresolved reference 'var3'", "id3", Some(DefaultExpressionId), "#var3"), _)) =>
    }
  }

  test("validates case expressions in switch") {
    val process = ScenarioBuilder
      .streaming("process1")
      .source("id1", "source")
      .switch("switch", "''", "var2", Case("#notExist", GraphBuilder.emptySink("end1", "sink")))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Unresolved reference 'notExist'", "switch", Some("end1"), "#notExist"), _)) =>
    }
  }

  test("not allow customNode outputVar when no return type in definition") {
    val processWithInvalidExpresssion =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "varName", "withoutReturnType", "par1" -> "'1'")
       .buildSimpleVariable("result-id2", "result", "''").emptySink("end-id2", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(vars, _), _)) if vars == Set("OutputVariable") =>
    }
  }

  test("require customNode outputVar when return type in definition") {
    val processWithInvalidExpresssion =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNodeNoOutput("custom", "customTransformer")
       .buildSimpleVariable("result-id2", "result", "''").emptySink("end-id2", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(vars, "custom"), Nil)) if vars == Set("OutputVariable") =>
    }
  }

  test("should validate process with parameters in different order than in definition") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "outVar", "withManyParameters",
          "long" -> "123123123133L", "lazyString" -> "'44'", "lazyInt" -> "43" )
       .buildSimpleVariable("result-id2", "result", "''").emptySink("end-id2", "sink")

    validate(process, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("should propagate error from source creation") {

    val base = ProcessDefinitionBuilder.withEmptyObjects(baseDefinition)
    val failingDefinition = base
      .copy(sourceFactories = base.sourceFactories
        .mapValues { case v:StandardObjectWithMethodDef => v.copy(methodDef = v.methodDef.copy(invocation = (_, _)
        => throw new RuntimeException("You passed incorrect parameter, cannot proceed"))) })

    val processWithInvalidExpresssion =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
       .buildSimpleVariable("result-id2", "result", "''").emptySink("end-id2", "sink")

    validateWithDef(processWithInvalidExpresssion, failingDefinition).result should matchPattern {
      case Invalid(NonEmptyList(CannotCreateObjectError("You passed incorrect parameter, cannot proceed", "id1"), Nil)) =>
    }

  }

  test("should be able to derive type from ServiceReturningType") {
    val base = ProcessDefinitionBuilder.withEmptyObjects(baseDefinition)
    val withServiceRef = base.copy(services = base.services + ("returningTypeService" ->
      new DefinitionExtractor(ProcessObjectDefinitionExtractor.service).extract(WithCategories(ServiceReturningTypeSample), SingleComponentConfig.zero)))

    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .enricher("serviceDef", "defined", "returningTypeService", "definition" -> "{param1: 'String', param2: 'Integer'}", "inRealTime" -> "#input.toString()")
        .emptySink("id2", "sink")

    val result = validateWithDef(process, withServiceRef)
    result.result should matchPattern {
      case Valid(_) =>
    }
    result.variablesInNodes("id2")("defined") shouldBe Typed.genericTypeClass[java.util.List[_]](
      List(TypedObjectTypingResult(ListMap("param1" -> Typed[String], "param2" -> Typed[Integer]))))

  }

  test("should override parameter definition from WithExplicitMethodToInvoke by definition from ServiceReturningType") {
    val base = ProcessDefinitionBuilder.withEmptyObjects(baseDefinition)
    val withServiceRef = base.copy(services = base.services + ("returningTypeService" ->
      new DefinitionExtractor(ProcessObjectDefinitionExtractor.service).extract(WithCategories(ServiceReturningTypeWithExplicitMethodSample), SingleComponentConfig.zero)))

    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .enricher("serviceDef", "defined", "returningTypeService", "definition" -> "{param1: 'String', param2: 'Integer'}", "inRealTime" -> "#input.toString()")
        .emptySink("id2", "sink")

    val result = validateWithDef(process, withServiceRef)
    result.result should matchPattern {
      case Valid(_) =>
    }
    result.variablesInNodes("id2")("defined") shouldBe Typed.genericTypeClass[java.util.List[_]](
      List(TypedObjectTypingResult(ListMap("param1" -> Typed[String], "param2" -> Typed[Integer]))))
  }

  test("should be able to run custom validation using ServiceReturningType") {
    val base = ProcessDefinitionBuilder.withEmptyObjects(baseDefinition)
    val withServiceRef = base.copy(services = base.services + ("withCustomValidation" ->
      new DefinitionExtractor(ProcessObjectDefinitionExtractor.service).extract(WithCategories(ServiceWithCustomValidation), SingleComponentConfig.zero)))

    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .enricher("service-1", "output1", "withCustomValidation",
          "age" -> "12",
          "fields" -> "{:}")
        .enricher("service-2", "output2", "withCustomValidation",
          "age" -> "30",
          "fields" -> "{invalid: 'yes'}")
        .enricher("service-3", "output3", "withCustomValidation",
          "age" -> "30",
          "fields" -> "{name: 12}")
       .buildSimpleVariable("result-id2", "result", "''").emptySink("end-id2", "sink")

    val result = validateWithDef(process, withServiceRef)

    result.result shouldBe Invalid(NonEmptyList.of(
      CustomNodeError("service-1", "Too young", Some("age")),
      CustomNodeError("service-2", "Service is invalid", None),
      CustomNodeError("service-3", "All values should be strings", Some("fields"))
    ))
  }


  test("not allows local variables in eager custom node parameter") {
    val processWithLocalVarInEagerParam =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "outVar", "withParamsTransformer",
          "par1" -> "#input.toString()" )
        .emptySink("id2", "sink")

    validate(processWithLocalVarInEagerParam, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Unresolved reference 'input'", "custom", Some("par1"), "#input.toString()"), Nil)) =>
    }
  }

  test("correctly detects lazy params") {
    val processWithLocalVarInEagerParam =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("custom", "outVar", "manyParams",
          "par1" -> "#input.toString()",
          "par2" -> "''",
          "par3" -> "#input.toString()",
          "par4" -> "''"
        )
        .emptySink("id2", "sink")

    validate(processWithLocalVarInEagerParam, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("allows using local variables for sink with lazy parameters") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .emptySink("sinkWithLazyParam","sinkWithLazyParam", "lazyString" -> "#input.toString()")

    validate(process, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("allow using process properties via meta.properties") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .additionalFields(properties = Map("property1" -> "value1"))
        .source("id1", "source")
        .buildSimpleVariable("var1", "var1", "#meta.properties.property1")
        .emptySink("sink", "sink")

    validate(process, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("hide meta variable using feature flag") {
    val process =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .buildSimpleVariable("var1", "var1", "#meta.processName")
        .emptySink("sink", "sink")

    validate(process, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }

    validate(process, baseDefinition.copy(expressionConfig = baseDefinition.expressionConfig.copy(hideMetaVariable = true))).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Unresolved reference 'meta'", _, _, _), Nil)) =>
    }
  }

  test("extract expression typing info") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", "source")
        .filter("filter", "true")
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map.empty,
      "filter" -> Map(DefaultExpressionId -> SpelExpressionTypingInfo(Map(PositionRange(0, 4) -> Typed.fromInstance(true)), Typed.fromInstance(true))),
      "sink" -> Map.empty
    )
  }

  test("extract expression typing info from source parameters") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", "sourceWithParam", "param" -> "123")
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map("param" -> SpelExpressionTypingInfo(Map(PositionRange(0, 3) -> Typed.fromInstance(123)), Typed.fromInstance(123))),
      "sink" -> Map.empty
    )
  }

  test("extract expression typing info from sink parameters") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source","source")
        .emptySink("sink", "sinkWithLazyParam", "lazyString" -> "'123'")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map.empty,
      "sink" -> Map(
        "lazyString" -> SpelExpressionTypingInfo(Map(PositionRange(0, 5) -> Typed.fromInstance("123")), Typed.fromInstance("123")))
    )
  }

  test("extract expression typing info from custom node parameters") {
    val process =
      ScenarioBuilder
        .streaming("process")
        .source("source", "source")
        .customNode("customNode", "out", "withParamsTransformer", "par1" -> "'123'")
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      "source" -> Map.empty,
      "customNode" -> Map("par1" -> SpelExpressionTypingInfo(Map(PositionRange(0, 5) -> Typed.fromInstance("123")), Typed.fromInstance("123"))),
      "sink" -> Map.empty
    )
  }

  test("validation of method returning future values") {
    val process = ScenarioBuilder.streaming("proc1")
      .source("id", "source")
      .buildSimpleVariable("sampleVar", "var", "#processHelper.futureValue")
      .buildSimpleVariable("sampleVar2", "var2", "#processHelper.identity(#var)")
      .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParserCompilationError("Mismatch parameter types. Found: identity(Future[String]). Required: identity(String)", _, _, _), Nil)) =>
    }
  }

  test ("validate with custom validator") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withCustomValidatorParam", "param" -> "'Aaaaa'")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("validate negatively with custom validator") {
    val processWithInvalidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withCustomValidatorParam", "param" -> "'Aaaaa'")
        .customNode("customNodeId2", "event1", "withCustomValidatorParam", "param" -> "'Baaaa'")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
      CustomParameterValidationError(_, _, "param", "customNodeId2"),
      Nil
      )) =>
    }
  }

  test ("use additional variables in expressions") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withAdditionalVariable", "param" -> "#additional.toString")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("hide variables in expression") {
    val processWithValidExpression =
      ScenarioBuilder
        .streaming("process1")
        .source("id1", "source")
        .customNode("customNodeId", "event", "withVariablesToHide", "param" -> "#input.toString")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParserCompilationError("Unresolved reference 'input'", "customNodeId", Some("param"), _),
      Nil)) =>
    }
  }

  private def validate(process: EspProcess, definitions: ProcessDefinition[ObjectDefinition]): CompilationResult[Unit] = {
    validateWithDef(process, ProcessDefinitionBuilder.withEmptyObjects(definitions))
  }

  private def validateWithDef(process: EspProcess, definitions: ProcessDefinition[ObjectWithMethodDef]): CompilationResult[Unit] = {
    ProcessValidator.default(definitions, new SimpleDictRegistry(Map.empty)).validate(process)
  }

  private val definitionWithTypedSource = baseDefinition.copy(sourceFactories
    = Map("source" -> ObjectDefinition.noParam.copy(returnType = Typed[SimpleRecord])))

  private val definitionWithTypedSourceAndTransformNode =
    definitionWithTypedSource.withCustomStreamTransformer("custom",
      classOf[AnotherSimpleRecord], emptyQueryNamesData, Parameter[String]("par1"))


  case class SimpleRecord(value1: AnotherSimpleRecord, plainValue: BigDecimal, plainValueOpt: Option[BigDecimal], intAsAny: Any, list: java.util.List[SimpleRecord]) {
    private val privateValue = "priv"

    def invoke1: Future[AnotherSimpleRecord] = ???

    def someMethod(a: Int): Int = ???
  }

  case class AnotherSimpleRecord(value2: Long)

  class SampleEnricher extends Service {
    def invoke()(implicit ec: ExecutionContext): Future[SimpleRecord] = Future.successful(SimpleRecord(AnotherSimpleRecord(1), 2, Option(2), 1, Collections.emptyList[SimpleRecord]))
  }

  object ProcessHelper {
    def add(a: Int, b: Int): Int = a + b

    def identity(nullableVal: String): String = nullableVal

    def futureValue: Future[String] = Future.successful("123")
  }

  object ServiceReturningTypeSample extends EagerService {

    @MethodToInvoke
    def invoke(@ParamName("definition") definition: java.util.Map[String, _],
               @ParamName("inRealTime") inRealTime: LazyParameter[String],
               @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation =
      EnricherContextTransformation(variableName, Typed.genericTypeClass[java.util.List[_]](List(
        TypingUtils.typeMapDefinition(definition))), new ServiceInvoker {
          override def invokeService(params: Map[String, Any])
                                (implicit ec: ExecutionContext,
                                 collector: InvocationCollectors.ServiceInvocationCollector,
                                 contextId: ContextId,
                                 componentUseCase: ComponentUseCase): Future[Any] = Future.successful(null)
        })

  }

  object ServiceReturningTypeWithExplicitMethodSample extends EagerServiceWithStaticParameters {

    override val hasOutput = true

    override def returnType(validationContext: ValidationContext,
                            parameters: Map[String, DefinedSingleParameter]): ValidatedNel[ProcessCompilationError, TypingResult] = {
      Valid(parameters
        .get("definition").collect {
          case DefinedEagerParameter(value: java.util.Map[String@unchecked, _], _) => TypingUtils.typeMapDefinition(value)
        }.map(param => Typed.genericTypeClass[java.util.List[_]](List(param)))
        .getOrElse(Unknown))
    }


    override def serviceImplementation(eagerParameters: Map[String, Any], typingResult: TypingResult, metaData: MetaData): ServiceInvoker = new ServiceInvoker {
      override def invokeService(params: Map[String, Any])
                                (implicit ec: ExecutionContext,
                                 collector: InvocationCollectors.ServiceInvocationCollector,
                                 contextId: ContextId,
                                 componentUseCase: ComponentUseCase): Future[Any] = Future.successful(null)

    }

    //@ParamName("definition") definition: java.util.Map[String, _], @ParamName("inRealTime") inRealTime: String
    override def parameters: List[Parameter] = List(
      Parameter.optional(
        name = "definition",
        typ = Typed.genericTypeClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown)),
      ),
      Parameter.optional(
        name = "inRealTime",
        typ = Typed.typedClass(classOf[String]),
      ).copy(isLazyParameter = true)
    )

  }

  object ServiceWithCustomValidation extends EagerService {

    @MethodToInvoke
    def invoke(@ParamName("age") age: Int,
               @ParamName("fields") fields: LazyParameter[java.util.Map[String, String]],
               @OutputVariableName variableName: String)(implicit nodeId: NodeId): ContextTransformation = {
      def returnType: ValidatedNel[ProcessCompilationError, TypingResult] = {
        if (age < 18) {
          Validated.invalidNel(CustomNodeError("Too young", Some("age")))
        } else {
          fields.returnType match {
            case TypedObjectTypingResult(fields, _, _) if fields.contains("invalid") =>
              Validated.invalidNel(CustomNodeError("Service is invalid", None))
            case TypedObjectTypingResult(fields, _, _) if fields.values.exists(_ != Typed.typedClass[String]) =>
              Validated.invalidNel(CustomNodeError("All values should be strings", Some("fields")))
            case _ => Valid(Typed.typedClass[String])
          }
        }
      }
      EnricherContextTransformation(variableName, returnType,
        new ServiceInvoker {
          override def invokeService(params: Map[String, Any])
                                    (implicit ec: ExecutionContext,
                                     collector: InvocationCollectors.ServiceInvocationCollector,
                                     contextId: ContextId,
                                     componentUseCase: ComponentUseCase): Future[Any] =
            Future.successful(s"name: ${params("fields").asInstanceOf[java.util.Map[String, String]].get("name")}, age: $age")
      })
    }
  }
}

class StartingWithACustomValidator extends CustomParameterValidator {
  override def name: String = "test_custom_validator"
  import cats.data.Validated.{invalid, valid}

  override def isValid(paramName: String, value: String, label: Option[String])(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] =
    if (value.stripPrefix("'").startsWith("A")) valid(Unit)
    else invalid(
      CustomParameterValidationError(s"Value $value does not starts with 'A'",
        "Value does not starts with 'A'", paramName, nodeId.id))
}
