package pl.touk.nussknacker.engine.compile

import java.util.Collections
import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.instances.string._
import com.github.ghik.silencer.silent
import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration, SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, _}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.compile.NodeTypingInfo._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef, StandardObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition, SinkAdditionalData}
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessObjectDefinitionExtractor}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.PositionRange
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.spel.SpelExpressionTypingInfo
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.engine.util.typing.TypingUtils
import pl.touk.nussknacker.engine.variables.MetaVariables

import scala.annotation.nowarn
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends FunSuite with Matchers with Inside {

  import spel.Implicits._

  private def emptyQueryNamesData(clearsContext: Boolean = false) = CustomTransformerAdditionalData(Set(), clearsContext, false, false)

  private val baseDefinition = ProcessDefinition[ObjectDefinition](
    Map("sampleEnricher" -> ObjectDefinition(List.empty, Typed[SimpleRecord], List()), "withParamsService" -> ObjectDefinition(List(Parameter[String]("par1")),
      Typed[SimpleRecord], List())),
    Map("source" -> ObjectDefinition(List.empty, Typed[SimpleRecord], List()),
        "sourceWithParam" -> ObjectDefinition(List(Parameter[Any]("param")), Typed[SimpleRecord], List()),
        "typedMapSource" -> ObjectDefinition(List(Parameter[TypedObjectDefinition]("type")), Typed[TypedMap], List())
    ),
    Map("sink" -> (ObjectDefinition.noParam, SinkAdditionalData(true)),
      "sinkWithLazyParam" -> (ObjectDefinition.withParams(List(Parameter[String]("lazyString").copy(isLazyParameter = true))), SinkAdditionalData(true))),

    Map("customTransformer" -> (ObjectDefinition(List.empty, Typed[SimpleRecord], List()), emptyQueryNamesData()),
      "withParamsTransformer" -> (ObjectDefinition(List(Parameter[String]("par1")), Typed[SimpleRecord], List()), emptyQueryNamesData()),
      "manyParams" -> (ObjectDefinition(List(
                Parameter[String]("par1").copy(isLazyParameter = true),
                Parameter[String]("par2"),
                Parameter[String]("par3").copy(isLazyParameter = true),
                Parameter[String]("par4")), Typed[SimpleRecord], List()), emptyQueryNamesData()),
      "clearingContextTransformer" -> (ObjectDefinition(List.empty, Typed[SimpleRecord], List()), emptyQueryNamesData(true)),
      "withManyParameters" -> (ObjectDefinition(List(
        Parameter[String]("lazyString").copy(isLazyParameter = true), Parameter[Integer]("lazyInt").copy(isLazyParameter = true),
        Parameter[Long]("long"))
      , Typed[SimpleRecord], List()), emptyQueryNamesData(true)),
      "withoutReturnType" -> (ObjectDefinition(List(Parameter[String]("par1")), Typed[Void], List()), emptyQueryNamesData()),
      "withMandatoryParams" -> (ObjectDefinition.withParams(List(Parameter[String]("mandatoryParam"))), emptyQueryNamesData()),
      "withNotBlankParams" -> (ObjectDefinition.withParams(List(NotBlankParameter("notBlankParam", Typed.typedClass(classOf[String])))), emptyQueryNamesData()),
      "withNullableLiteralIntegerParam" -> (ObjectDefinition.withParams(List(
        Parameter[Integer]("nullableLiteralIntegerParam").copy(validators = List(LiteralParameterValidator.integerValidator))
      )), emptyQueryNamesData()),
      "withRegExpParam" -> (ObjectDefinition.withParams(List(
        Parameter[Integer]("regExpParam").copy(validators = List(LiteralParameterValidator.numberValidator))
      )), emptyQueryNamesData()),
      "withJsonParam" -> (ObjectDefinition.withParams(List(
        Parameter[String]("jsonParam").copy(validators = List(JsonValidator))
      )), emptyQueryNamesData()),
      "withCustomValidatorParam" -> (ObjectDefinition.withParams(List(
        Parameter[String]("param").copy(validators = List(CustomParameterValidatorDelegate("test_custom_validator")))
      )), emptyQueryNamesData()),
      "withAdditionalVariable" -> (ObjectDefinition.withParams(List(
        Parameter[String]("param").copy(additionalVariables = Map("additional" -> Typed[Int]), isLazyParameter = true)
      )), emptyQueryNamesData()),
      "withVariablesToHide" -> (ObjectDefinition.withParams(List(
        Parameter[String]("param").copy(variablesToHide = Set("input"), isLazyParameter = true)
      )), emptyQueryNamesData())
    ),
    Map.empty,
    ObjectDefinition.noParam,
    ExpressionDefinition(
      Map("processHelper" -> ObjectDefinition(List(), Typed(ProcessHelper.getClass), List("cat1"), SingleNodeConfig.zero)),
      List.empty, LanguageConfiguration.default, optimizeCompilation = false, strictTypeChecking = true, dictionaries = Map.empty, hideMetaVariable = false, strictMethodsChecking = true
    ),
    ClassExtractionSettings.Default
  )

  test("validated with success") {
    val correctProcess = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
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
      .sink("id2", "#processHelper.add(1, 2)", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.variablesInNodes shouldBe Map(
      ExceptionHandlerNodeId -> Map("meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "id1" -> Map("meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "filter1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "filter2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "filter3" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "sampleProcessor1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "sampleProcessor2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass)),
      "bv1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass), "out" -> Typed[SimpleRecord]),
      "id2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ProcessHelper.getClass), "out" -> Typed[SimpleRecord],
        "vars" -> TypedObjectTypingResult(ListMap(
          "v1" -> Typed[Integer],
          "mapVariable" -> TypedObjectTypingResult(ListMap("Field1" -> Typed[String], "Field2" -> Typed[String], "Field3" -> Typed[BigDecimal])),
          "spelVariable" ->  Typed[Boolean]
        ))
      )
    )
  }

  test("allow global variables in source definition") {
    val correctProcess = EspProcessBuilder
          .id("process1")
          .exceptionHandler()
          .source("id1", "sourceWithParam", "param" -> "#processHelper")
          .sink("id2", "#input", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }
  }

  test("find duplicated ids") {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = EspProcessBuilder.id("process1").exceptionHandler().source(duplicatedId, "source").emptySink(duplicatedId, "sink")
    validate(processWithDuplicatedIds, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  test("find duplicated ids in switch") {
    val duplicatedId = "id1"
    val processWithDuplicatedIds =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .sink("id2", "wtf!!!", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError(_, _, _, _), _)) =>
    }
  }

  test ("find mandatory expressions for mandatory parameters") {
    val processWithInvalidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "withMandatoryParams", "mandatoryParam" -> "")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(EmptyMandatoryParameter(_, _, "mandatoryParam", "customNodeId"), _)) =>
    }
  }

  test ("find blank expressions for notBlank parameter") {
    val processWithInvalidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "withNullableLiteralIntegerParam", "nullableLiteralIntegerParam" -> "12")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("valid for Nullable Literal Integer param") {
    val processWithInvalidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "withNullableLiteralIntegerParam", "nullableLiteralIntegerParam" -> "")
        .emptySink("emptySink", "sink")

    validate(processWithInvalidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("invalid for Nullable Literal Integer param") {
    val processWithInvalidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "withJsonParam", "jsonParam" -> "'{\"example\": \"json\"}'")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("invalid for json param") {
    val processWithInvalidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "customTransformer")
        .processor("id2", missingServiceId, "foo" -> "'bar'")
        .sink("sink", "#event.plainValue", "sink")

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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .processorEnd("id2", serviceId, redundantServiceParameter -> "'bar'")

    validate(processWithInvalidServiceInvocation, definition).result should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(_, _), _)) =>
    }
  }

  test("find missing source") {
    val serviceId = "serviceId"
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      "simpleVar" -> Typed[String]
    )

  }

  test("find missing custom node") {
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "out", "notExisting", "dummy" -> "input")
        .emptySink("id2", "sink")

    validate(processWithRefToMissingService, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(MissingCustomNodeExecutor("notExisting", "custom"), Nil)) =>
    }
  }

  test("find missing parameter for exception handler") {
    val process = EspProcessBuilder.id("process1").exceptionHandler().source("id1", "source").emptySink("id2", "sink")
    val definition = baseDefinition.withExceptionHandlerFactory(Parameter[String]("foo"))
    validate(process, definition).result should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(_, _), _)) =>
    }
  }

  test("find usage of unresolved plain variables") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildVariable("bv1", "doesExist", "v1" -> "42")
      .filter("sampleFilter", "#doesExist['v1'] + #doesNotExist1 + #doesNotExist2 > 10")
      .emptySink("id2", "sink")
    validate(process, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParseError("Unresolved reference 'doesNotExist1'", "sampleFilter", Some(DefaultExpressionId), _),
      List(ExpressionParseError("Unresolved reference 'doesNotExist2'", "sampleFilter", Some(DefaultExpressionId), _)))) =>
    }
  }

  test("find usage of non references") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValue > 10")
      .filter("sampleFilter2", "input.plainValue > 10")
      .emptySink("id2", "sink")
    validate(process, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Non reference 'input' occurred. Maybe you missed '#' in front of it?", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
  }

  test("find usage of fields that does not exist in object") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.value1.value2 > 10")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type: AnotherSimpleRecord", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
  }


  test("find not existing variables after custom node") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type: AnotherSimpleRecord", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
  }

  test("find not existing variables after split") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .split("split1",
        GraphBuilder.emptySink("id2", "sink"),
        GraphBuilder.filter("sampleFilter2", "#input.value1.value3 > 10").emptySink("id3", "sink")
      )

    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    val compilationResult = validate(process, definitionWithCustomNode)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type: AnotherSimpleRecord", "sampleFilter2", Some(DefaultExpressionId), _), _)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ProcessHelper.getClass))
    compilationResult.variablesInNodes("id3") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ProcessHelper.getClass))
  }

  test("validate custom node return type") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNodeNoOutput("noOutput", "withoutReturnType", "par1" -> "'1'")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter1", "#out1.value2 > 0")
      .filter("sampleFilter2", "#out1.terefere")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type: AnotherSimpleRecord",
      "sampleFilter2", Some(DefaultExpressionId), "#out1.terefere"), _)) =>
    }
  }

  test("doesn't allow unknown vars in custom node params") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "#strangeVar")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    validate(process, definitionWithCustomNode).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference 'strangeVar'", "cNode1", Some("par1"), "#strangeVar"), _)) =>
    }
  }

  test("validate exception handler params") {

    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .emptySink("id2", "sink")
    val definitionWithExceptionHandlerWithParams = baseDefinition.copy(exceptionHandlerFactory =
      ObjectDefinition.withParams(List(Parameter[String]("param1"))))

    inside (validate(process, definitionWithExceptionHandlerWithParams).result) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "$exceptionHandler"), _)) => missingParam shouldBe Set("param1")
    }
  }



  test("not validate exception handler params in subprocess") {

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData(), true), ExceptionHandlerRef(List()),
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink", List()), Some("'deadEnd'")))), List.empty)

    val definitionWithExceptionHandlerWithParams = baseDefinition.copy(exceptionHandlerFactory =
      ObjectDefinition.withParams(List(Parameter[String]("param1"))))

    validate(ProcessCanonizer.uncanonize(subprocess).toOption.get, definitionWithExceptionHandlerWithParams).result shouldBe 'valid
  }


  test("validate service params") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .enricher("enricher1", "out", "withParamsService")
      .emptySink("id2", "sink")

    inside (validate(process, definitionWithTypedSource).result) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "enricher1"), _)) => missingParam shouldBe Set("par1")
    }
  }

  test("find usage of fields that does not exist in option object") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValueOpt.terefere > 10")
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type: BigDecimal", "sampleFilter1", Some(DefaultExpressionId), _), _)) =>
    }
  }

  test("return field/property names in errors") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
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
      ExpressionParseError("Expression [{] @0: EL1044E: Unexpectedly ran out of input", "c1", Some("par1"), _),
      List(
      ExpressionParseError("Unresolved reference 'terefere'", "p1", Some("par1"), _),
      ExpressionParseError("Unresolved reference 'terefere22'", "v1", Some("par1"), _))
      )) =>
    }
  }

  test("not allow to overwrite variable by variable node") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .buildSimpleVariable("var1overwrite", "var1", "''")
      .emptySink("id2", "sink")
    val compilationResult = validate(process, definitionWithTypedSource)
    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ProcessHelper.getClass), "var1" -> Typed[String])
  }

  test("not allow to overwrite variable by switch node") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .switch("var1overwrite", "''", "var1", GraphBuilder.emptySink("id2", "sink"))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("not allow to overwrite variable by enricher node") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .enricher("var1overwrite", "var1", "sampleEnricher")
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("not allow to overwrite variable by variable builder") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .buildVariable("var1overwrite", "var1", "a" -> "''")
      .emptySink("id2", "sink")
    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("validate variable builder fields usage") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildVariable("valr", "var1", "a" -> "''", "b" -> "11")
      .buildSimpleVariable("working", "var2", "#var1.b > 10")
      .buildSimpleVariable("notWorking", "var3", "#var1.a > 10")
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Wrong part types", "notWorking", Some(DefaultExpressionId), "#var1.a > 10"), _)) =>
    }
  }

  test("not allow to overwrite variable by custom node") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .customNode("var1overwrite", "var1", "custom", "par1" -> "''")
      .emptySink("id2", "sink")

    validate(process, definitionWithTypedSourceAndTransformNode).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite", _), _)) =>
    }
  }

  test("allow different vars in branches") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
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
      "var2" -> Typed[String],
      "var3" -> Typed[String])
    compilationResult.variablesInNodes("id3") shouldBe Map(
      "input" -> Typed[SimpleRecord],
      "meta" -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ProcessHelper.getClass),
      "var2" -> Typed[String],
      "var3" -> Typed[Int]
    )
  }

  test("not allow to use vars from different branches") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .switch("switch", "''", "var2",
        GraphBuilder.buildSimpleVariable("var3", "var3", "''").emptySink("id2", "sink"),
         Case("false", GraphBuilder.sink("id3", "#var3", "sink")))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference 'var3'", "id3", Some(DefaultExpressionId), "#var3"), _)) =>
    }
  }

  test("not allow customNode outputVar when no return type in definition") {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "varName", "withoutReturnType", "par1" -> "'1'")
        .sink("id2", "''", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(vars, _), _)) if vars == Set("OutputVariable") =>
    }
  }

  test("detect clearing context in custom transformer") {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "varName", "clearingContextTransformer")
        .sink("id2", "#input.toString()", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference 'input'", "id2", Some(DefaultExpressionId), "#input.toString()"), _)) =>
    }
  }

  test("can use global variables after clearing context") {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "varName", "clearingContextTransformer")
        .sink("id2", "#processHelper.toString() + #meta.processName", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("require customNode outputVar when return type in definition") {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNodeNoOutput("custom", "customTransformer")
        .sink("id2", "''", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(vars, "custom"), Nil)) if vars == Set("OutputVariable") =>
    }
  }

  test("should validate process with parameters in different order than in definition") {
    val process =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "outVar", "withManyParameters",
          "long" -> "123L", "lazyString" -> "'44'", "lazyInt" -> "43" )
        .sink("id2", "''", "sink")

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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .sink("id2", "''", "sink")

    validateWithDef(processWithInvalidExpresssion, failingDefinition).result should matchPattern {
      case Invalid(NonEmptyList(CannotCreateObjectError("You passed incorrect parameter, cannot proceed", "id1"), Nil)) =>
    }

  }

  test("should be able to derive type from ServiceReturningType") {
    val base = ProcessDefinitionBuilder.withEmptyObjects(baseDefinition)
    val withServiceRef = base.copy(services = base.services + ("returningTypeService" ->
      new DefinitionExtractor(ProcessObjectDefinitionExtractor.service).extract(WithCategories(ServiceReturningTypeSample), SingleNodeConfig.zero)))

    val process =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .enricher("serviceDef", "defined", "returningTypeService", "definition" -> "{param1: 'String', param2: 'Integer'}", "inRealTime" -> "#input.toString()")
        .sink("id2", "''", "sink")

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
      new DefinitionExtractor(ProcessObjectDefinitionExtractor.service).extract(WithCategories(ServiceReturningTypeWithExplicitMethodSample), SingleNodeConfig.zero)))

    val process =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .enricher("serviceDef", "defined", "returningTypeService", "definition" -> "{param1: 'String', param2: 'Integer'}", "inRealTime" -> "#input.toString()")
        .sink("id2", "''", "sink")

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
      new DefinitionExtractor(ProcessObjectDefinitionExtractor.service).extract(WithCategories(ServiceWithCustomValidation), SingleNodeConfig.zero)))

    val process =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .enricher("service-1", "output-1", "withCustomValidation",
          "age" -> "12",
          "fields" -> "{:}")
        .enricher("service-2", "output-2", "withCustomValidation",
          "age" -> "30",
          "fields" -> "{invalid: 'yes'}")
        .enricher("service-3", "output-3", "withCustomValidation",
          "age" -> "30",
          "fields" -> "{name: 12}")
        .sink("id2", "''", "sink")

    val result = validateWithDef(process, withServiceRef)

    result.result shouldBe Invalid(NonEmptyList.of(
      CustomNodeError("service-1", "Too young", Some("age")),
      CustomNodeError("service-2", "Service is invalid", None),
      CustomNodeError("service-3", "All values should be strings", Some("fields"))
    ))
  }


  test("not allows local variables in eager custom node parameter") {
    val processWithLocalVarInEagerParam =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "outVar", "withParamsTransformer",
          "par1" -> "#input.toString()" )
        .emptySink("id2", "sink")

    validate(processWithLocalVarInEagerParam, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference 'input'", "custom", Some("par1"), "#input.toString()"), Nil)) =>
    }
  }

  test("correctly detects lazy params") {
    val processWithLocalVarInEagerParam =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .emptySink("sinkWithLazyParam","sinkWithLazyParam", "lazyString" -> "#input.toString()")

    validate(process, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("allow using process properties via meta.properties") {
    val process =
      EspProcessBuilder
        .id("process1")
        .additionalFields(properties = Map("property1" -> "value1"))
        .exceptionHandler()
        .source("id1", "source")
        .buildSimpleVariable("var1", "var1", "#meta.properties.property1")
        .emptySink("sink", "sink")

    validate(process, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test("hide meta variable using feature flag") {
    val process =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .buildSimpleVariable("var1", "var1", "#meta.processName")
        .emptySink("sink", "sink")

    validate(process, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }

    validate(process, baseDefinition.copy(expressionConfig = baseDefinition.expressionConfig.copy(hideMetaVariable = true))).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference 'meta'", _, _, _), Nil)) =>
    }
  }

  test("extract expression typing info") {
    val process =
      EspProcessBuilder
        .id("process")
        .exceptionHandler()
        .source("source", "source")
        .filter("filter", "true")
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      ExceptionHandlerNodeId -> Map.empty,
      "source" -> Map.empty,
      "filter" -> Map(DefaultExpressionId -> SpelExpressionTypingInfo(Map(PositionRange(0, 4) -> Typed[Boolean]), Typed[Boolean])),
      "sink" -> Map.empty
    )
  }

  test("extract expression typing info from source parameters") {
    val process =
      EspProcessBuilder
        .id("process")
        .exceptionHandler()
        .source("source", "sourceWithParam", "param" -> "123")
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      ExceptionHandlerNodeId -> Map.empty,
      "source" -> Map("param" -> SpelExpressionTypingInfo(Map(PositionRange(0, 3) -> Typed[java.lang.Integer]), Typed[java.lang.Integer])),
      "sink" -> Map.empty
    )
  }

  test("extract expression typing info from sink parameters") {
    val process =
      EspProcessBuilder
        .id("process")
        .exceptionHandler()
        .source("source", "source")
        .sink("sink", "'123'" , "sinkWithLazyParam", ("lazyString" -> "'123'"))

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      ExceptionHandlerNodeId -> Map.empty,
      "source" -> Map.empty,
      "sink" -> Map(
        DefaultExpressionId -> SpelExpressionTypingInfo(Map(PositionRange(0, 5) -> Typed[String]), Typed[String]),
        "lazyString" -> SpelExpressionTypingInfo(Map(PositionRange(0, 5) -> Typed[String]), Typed[String]))
    )
  }

  test("extract expression typing info from custom node parameters") {
    val process =
      EspProcessBuilder
        .id("process")
        .exceptionHandler()
        .source("source", "source")
        .customNode("customNode", "out", "withParamsTransformer", "par1" -> "'123'")
        .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.expressionsInNodes shouldEqual Map(
      ExceptionHandlerNodeId -> Map.empty,
      "source" -> Map.empty,
      "customNode" -> Map("par1" -> SpelExpressionTypingInfo(Map(PositionRange(0, 5) -> Typed[String]), Typed[String])),
      "sink" -> Map.empty
    )
  }

  test("validation of method returning future values") {
    val process = EspProcessBuilder.id("proc1")
      .exceptionHandler()
      .source("id", "source")
      .buildSimpleVariable("sampleVar", "var", "#processHelper.futureValue")
      .buildSimpleVariable("sampleVar2", "var2", "#processHelper.identity(#var)")
      .emptySink("sink", "sink")

    val compilationResult = validate(process, baseDefinition)

    compilationResult.result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Mismatch parameter types. Found: identity(Future[String]). Required: identity(String)", _, _, _), Nil)) =>
    }
  }

  test ("validate with custom validator") {
    val processWithValidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "withCustomValidatorParam", "param" -> "'Aaaaa'")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("validate negatively with custom validator") {
    val processWithInvalidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
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
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "withAdditionalVariable", "param" -> "#additional.toString")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Valid(_) =>
    }
  }

  test ("hide variables in expression") {
    val processWithValidExpression =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "withVariablesToHide", "param" -> "#input.toString")
        .emptySink("emptySink", "sink")

    validate(processWithValidExpression, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParseError("Unresolved reference 'input'", "customNodeId", Some("param"), _),
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
      classOf[AnotherSimpleRecord], emptyQueryNamesData(), Parameter[String]("par1"))


  case class SimpleRecord(value1: AnotherSimpleRecord, plainValue: BigDecimal, plainValueOpt: Option[BigDecimal], intAsAny: Any, list: java.util.List[SimpleRecord]) {
    private val privateValue = "priv"

    def invoke1: Future[AnotherSimpleRecord] = ???

    def someMethod(a: Int): Int = ???
  }

  case class AnotherSimpleRecord(value2: Long)

  class SampleEnricher extends Service {
    def invoke()(implicit ec: ExecutionContext) = Future.successful(SimpleRecord(AnotherSimpleRecord(1), 2, Option(2), 1, Collections.emptyList[SimpleRecord]))
  }

  object ProcessHelper {
    def add(a: Int, b: Int) = a + b

    def identity(nullableVal: String): String = nullableVal

    def futureValue: Future[String] = Future.successful("123")
  }

  // Remove @silent after upgrade to silencer 1.7
  @silent("deprecated")
  @nowarn("cat=deprecation")
  object ServiceReturningTypeSample extends Service with ServiceReturningType {

    @MethodToInvoke
    def invoke(@ParamName("definition") definition: java.util.Map[String, _], @ParamName("inRealTime") inRealTime: String): Future[AnyRef] = Future.successful(null)

    //returns list of type defined by definition parameter
    override def returnType(parameters: Map[String, (TypingResult, Option[Any])]): typing.TypingResult = {
      parameters
        .get("definition")
        .flatMap(_._2)
        .map(definition => TypingUtils.typeMapDefinition(definition.asInstanceOf[java.util.Map[String, _]]))
        .map(param => Typed.genericTypeClass[java.util.List[_]](List(param)))
        .getOrElse(Unknown)
    }
  }

  // Remove @silent after upgrade to silencer 1.7
  @silent("deprecated")
  @nowarn("cat=deprecation")
  object ServiceReturningTypeWithExplicitMethodSample extends Service with ServiceReturningType with ServiceWithExplicitMethod {

    override def returnType(parameters: Map[String, (TypingResult, Option[Any])]): typing.TypingResult = {
      parameters
        .get("definition")
        .flatMap(_._2)
        .map(definition => TypingUtils.typeMapDefinition(definition.asInstanceOf[java.util.Map[String, _]]))
        .map(param => Typed.genericTypeClass[java.util.List[_]](List(param)))
        .getOrElse(Unknown)
    }

    override def invokeService(params: List[AnyRef])
                              (implicit ec: ExecutionContext,
                               collector: InvocationCollectors.ServiceInvocationCollector,
                               metaData: MetaData,
                               contextId: ContextId): Future[AnyRef] = Future.successful(null)


    //@ParamName("definition") definition: java.util.Map[String, _], @ParamName("inRealTime") inRealTime: String
    override def parameterDefinition: List[Parameter] = List(
      Parameter(
        name = "definition",
        typ = TypedClass(classOf[java.util.Map[_, _]], List(Typed[String], Unknown)),
        validators = Nil
      ),
      Parameter(
        name = "inRealTime",
        typ = Typed.typedClass(classOf[String]),
        validators = Nil
      )
    )

    // this definition (from ServiceWithExplicitMethod) should be overridden by definition from ServiceReturningType
    override def returnType: TypingResult = Typed.typedClass(classOf[String])
  }


  // Remove @silent after upgrade to silencer 1.7
  @silent("deprecated")
  @nowarn("cat=deprecation")
  object ServiceWithCustomValidation extends Service with ServiceReturningType {

    @MethodToInvoke
    def invoke(@ParamName("age") age: Int,
               @ParamName("fields") fields: java.util.Map[String, String]): Future[String] = {
      Future.successful(s"name: ${fields.get("name")}, age: $age")
    }

    def returnType(params: Map[String, (TypingResult, Option[Any])]): TypingResult = {
      if (params("age")._2.get.asInstanceOf[Int] < 18) {
        throw CustomNodeValidationException("Too young", Some("age"))
      }
      params("fields")._1 match {
        case TypedObjectTypingResult(fields, _, _) if fields.contains("invalid") =>
          throw CustomNodeValidationException("Service is invalid", None)
        case TypedObjectTypingResult(fields, _, _) if fields.values.exists(_ != Typed.typedClass[String]) =>
          throw CustomNodeValidationException("All values should be strings", Some("fields"))
        case _ => Typed.typedClass[String]
      }
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