package pl.touk.nussknacker.engine.compile

import java.util.Collections

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import cats.instances.string._
import org.scalatest.{FunSuite, Inside, Matchers}
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.lazyy.ContextWithLazyValuesProvider
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, LanguageConfiguration, SingleNodeConfig, WithCategories}
import pl.touk.nussknacker.engine.api.typed._
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ProcessDefinition, SinkAdditionalData}
import pl.touk.nussknacker.engine.definition.{DefinitionExtractor, ProcessObjectDefinitionExtractor}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.SubprocessInputDefinition.{SubprocessClazzRef, SubprocessParameter}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder
import pl.touk.nussknacker.engine.types.TypesInformationExtractor
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder._
import pl.touk.nussknacker.engine.testing.ProcessDefinitionBuilder.ObjectProcessDefinition
import pl.touk.nussknacker.engine.util.typing.TypingUtils
import pl.touk.nussknacker.engine.variables.MetaVariables

import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends FunSuite with Matchers with Inside {

  import spel.Implicits._

  private def emptyQueryNamesData(clearsContext: Boolean = false) = CustomTransformerAdditionalData(Set(), clearsContext, false)

  private val baseDefinition = ProcessDefinition[ObjectDefinition](
    Map("sampleEnricher" -> ObjectDefinition(List.empty, ClazzRef[SimpleRecord], List()), "withParamsService" -> ObjectDefinition(List(Parameter("par1",
      ClazzRef[String])), ClazzRef[SimpleRecord], List())),
    Map("source" -> ObjectDefinition(List.empty, ClazzRef[SimpleRecord], List()),
        "sourceWithParam" -> ObjectDefinition(List(Parameter("param", ClazzRef[Any])), ClazzRef[SimpleRecord], List()),
        "typedMapSource" -> ObjectDefinition(List(Parameter("type", ClazzRef[TypedObjectDefinition])), ClazzRef[TypedMap], List())
    ),
    Map("sink" -> (ObjectDefinition.noParam, SinkAdditionalData(true)),
      "sinkWithLazyParam" -> (ObjectDefinition.withParams(List(Parameter("lazyString", Typed[String], classOf[LazyParameter[_]]))), SinkAdditionalData(true))),

    Map("customTransformer" -> (ObjectDefinition(List.empty, ClazzRef[SimpleRecord], List()), emptyQueryNamesData()),
      "withParamsTransformer" -> (ObjectDefinition(List(Parameter("par1", ClazzRef[String])), ClazzRef[SimpleRecord], List()), emptyQueryNamesData()),
      "manyParams" -> (ObjectDefinition(List(
                Parameter("par1", Typed[String], classOf[LazyParameter[_]]),
                Parameter("par2", ClazzRef[String]),
                Parameter("par3", Typed[String], classOf[LazyParameter[_]]),
                Parameter("par4", ClazzRef[String])), ClazzRef[SimpleRecord], List()), emptyQueryNamesData()),
      "clearingContextTransformer" -> (ObjectDefinition(List.empty, ClazzRef[SimpleRecord], List()), emptyQueryNamesData(true)),
      "withManyParameters" -> (ObjectDefinition(List(
        Parameter("lazyString", Typed[String], classOf[LazyParameter[_]]), Parameter("lazyInt", Typed[Integer], classOf[LazyParameter[_]]),
        Parameter("long", ClazzRef[Long]))
      , ClazzRef[SimpleRecord], List()), emptyQueryNamesData(true)),
      "withoutReturnType" -> (ObjectDefinition(List(Parameter("par1", ClazzRef[String])), ClazzRef[Void], List()), emptyQueryNamesData())
    ),
    Map.empty,
    ObjectDefinition.noParam,
    ExpressionDefinition(
      Map("processHelper" -> ObjectDefinition(List(), Typed(ClazzRef(ProcessHelper.getClass)), List("cat1"), SingleNodeConfig.zero)),
      List.empty, LanguageConfiguration.default, optimizeCompilation = false
    ),
    TypesInformationExtractor.clazzAndItsChildrenDefinition(List(Typed[SampleEnricher], Typed[SimpleRecord], Typed(ProcessHelper.getClass)))(ClassExtractionSettings.Default)
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
      .sink("id2", "#processHelper.add(#processHelper, 2)", "sink")

    val compilationResult = validate(correctProcess, baseDefinition)

    compilationResult.result should matchPattern {
      case Valid(_) =>
    }

    compilationResult.variablesInNodes shouldBe Map(
      "id1" -> Map("meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass))),
      "filter1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass))),
      "filter2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass))),
      "filter3" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass))),
      "sampleProcessor1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass))),
      "sampleProcessor2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass))),
      "bv1" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)), "out" -> Typed[SimpleRecord]),
      "id2" -> Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(correctProcess.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)), "out" -> Typed[SimpleRecord],
        "vars" -> TypedObjectTypingResult(Map(
          "v1" -> Typed[Integer],
          "mapVariable" -> TypedObjectTypingResult(Map("Field1" -> Typed[String], "Field2" -> Typed[String], "Field3" -> Typed[BigDecimal])),
          "spelVariable" ->  Typed(ClazzRef[Boolean])

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
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .sink("id2", "wtf!!!", "sink")

    validate(processWithInvalidExpresssion, baseDefinition).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError(_, _, _, _), _)) =>
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
      "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)),
      "event" -> Typed[SimpleRecord]
    )

    val validDefinition = baseDefinition.withService(missingServiceId, Parameter(name = "foo", typ = ClazzRef[String]))
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

    val definition = ProcessDefinitionBuilder.empty.withService(serviceId, Parameter(name = "foo", typ = ClazzRef[String]))
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
    val definition = baseDefinition.withExceptionHandlerFactory(Parameter(name = "foo", typ = ClazzRef[String]))
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
      ExpressionParseError("Unresolved reference doesNotExist1", "sampleFilter", None, _),
      List(ExpressionParseError("Unresolved reference doesNotExist2", "sampleFilter", None, _)))) =>
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
      case Invalid(NonEmptyList(ExpressionParseError("Non reference 'input' occurred. Maybe you missed '#' in front of it?", "sampleFilter2", None, _), _)) =>
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
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type: pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord", "sampleFilter2", None, _), _)) =>
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
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type: pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord", "sampleFilter2", None, _), _)) =>
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
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type: pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord", "sampleFilter2", None, _), _)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)))
    compilationResult.variablesInNodes("id3") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)))
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
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type: pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord",
      "sampleFilter2", None, "#out1.terefere"), _)) =>
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
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference strangeVar", "cNode1", Some("par1"), "#strangeVar"), _)) =>
    }
  }

  test("validate exception handler params") {

    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .emptySink("id2", "sink")
    val definitionWithExceptionHandlerWithParams = baseDefinition.copy(exceptionHandlerFactory =
      ObjectDefinition.withParams(List(Parameter("param1", ClazzRef[String]))))

    inside (validate(process, definitionWithExceptionHandlerWithParams).result) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "$process"), _)) => missingParam shouldBe Set("param1")
    }
  }



  test("not validate exception handler params in subprocess") {

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData(), true), ExceptionHandlerRef(List()),
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(SubprocessParameter("param", SubprocessClazzRef[String])))),
        canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink", List()), Some("'deadEnd'")))), None)

    val definitionWithExceptionHandlerWithParams = baseDefinition.copy(exceptionHandlerFactory =
      ObjectDefinition.withParams(List(Parameter("param1", ClazzRef[String]))))

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
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type: scala.math.BigDecimal", "sampleFilter1", None, _), _)) =>
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
      ExpressionParseError("Unresolved reference terefere", "p1", Some("par1"), _),
      ExpressionParseError("Unresolved reference terefere22", "v1", Some("par1"), _))
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
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
    }
    compilationResult.variablesInNodes("id2") shouldBe Map("input" -> Typed[SimpleRecord], "meta" -> MetaVariables.typingResult(process.metaData), "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)), "var1" -> Typed[String])
  }

  test("not allow to overwrite variable by switch node") {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .switch("var1overwrite", "''", "var1", GraphBuilder.emptySink("id2", "sink"))

    validate(process, definitionWithTypedSource).result should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
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
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
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
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
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
      case Invalid(NonEmptyList(ExpressionParseError("Wrong part types", "notWorking", None, "#var1.a > 10"), _)) =>
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
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
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
      "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)),
      "var2" -> Typed[String],
      "var3" -> Typed[String])
    compilationResult.variablesInNodes("id3") shouldBe Map(
      "input" -> Typed[SimpleRecord],
      "meta" -> MetaVariables.typingResult(process.metaData),
      "processHelper" -> Typed(ClazzRef(ProcessHelper.getClass)),
      "var2" -> Typed[String],
      "var3" -> Typed(ClazzRef[Int])
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
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference var3", "id3", None, "#var3"), _)) =>
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
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference input", "id2", None, "#input.toString()"), _)) =>
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
        .mapValues(v => v.copy(methodDef = v.methodDef.copy(invocation = (_, _)
        => throw new IllegalArgumentException("You passed incorrect parameter, cannot proceed")))))

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
    result.variablesInNodes("id2")("defined") shouldBe TypedClass(classOf[java.util.List[_]],
      List(TypedObjectTypingResult(Map("param1" -> Typed[String], "param2" -> Typed[Integer]))))


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
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference input", "custom", Some("par1"), "#input.toString()"), Nil)) =>
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

  private def validate(process: EspProcess, definitions: ProcessDefinition[ObjectDefinition]): CompilationResult[Unit] = {
    validateWithDef(process, ProcessDefinitionBuilder.withEmptyObjects(definitions))
  }

  private def validateWithDef(process: EspProcess, definitions: ProcessDefinition[ObjectWithMethodDef]): CompilationResult[Unit] = {
    ProcessValidator.default(definitions).validate(process)
  }

  private val definitionWithTypedSource = baseDefinition.copy(sourceFactories
    = Map("source" -> ObjectDefinition.noParam.copy(returnType = Typed[SimpleRecord])))

  private val definitionWithTypedSourceAndTransformNode =
    definitionWithTypedSource.withCustomStreamTransformer("custom",
      classOf[AnotherSimpleRecord], emptyQueryNamesData(), Parameter("par1", ClazzRef[String]))


  case class SimpleRecord(value1: AnotherSimpleRecord, plainValue: BigDecimal, plainValueOpt: Option[BigDecimal], intAsAny: Any, list: java.util.List[SimpleRecord]) {
    private val privateValue = "priv"

    def invoke1: Future[AnotherSimpleRecord] = ???

    def invoke2: State[ContextWithLazyValuesProvider, AnotherSimpleRecord] = ???

    def someMethod(a: Int): Int = ???
  }

  case class AnotherSimpleRecord(value2: Long)

  class SampleEnricher extends Service {
    def invoke()(implicit ec: ExecutionContext) = Future.successful(SimpleRecord(AnotherSimpleRecord(1), 2, Option(2), 1, Collections.emptyList[SimpleRecord]))
  }

  object ProcessHelper {
    def add(a: Int, b: Int) = a + b
  }

  object ServiceReturningTypeSample extends Service with ServiceReturningType {

    @MethodToInvoke
    def invoke(@ParamName("definition") definition: java.util.Map[String, _], @ParamName("inRealTime") inRealTime: String): Future[AnyRef] = Future.successful(null)

    //returns list of type defined by definition parameter
    override def returnType(parameters: Map[String, (TypingResult, Option[Any])]): typing.TypingResult = {
      parameters
        .get("definition")
        .flatMap(_._2)
        .map(definition => TypingUtils.typeMapDefinition(definition.asInstanceOf[java.util.Map[String, _]]))
        .map(param => TypedClass(classOf[java.util.List[_]], List(param)))
        .getOrElse(Unknown)
    }
  }

}