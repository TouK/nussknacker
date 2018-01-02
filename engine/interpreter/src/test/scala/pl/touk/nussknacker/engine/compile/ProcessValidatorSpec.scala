package pl.touk.nussknacker.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.{MetaData, Service, StreamMetaData}
import pl.touk.nussknacker.engine.api.lazyy.ContextWithLazyValuesProvider
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, WithCategories}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.{CanonicalProcess, canonicalnode}
import pl.touk.nussknacker.engine.compile.ProcessCompilationError._
import pl.touk.nussknacker.engine.definition.DefinitionExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, Parameter}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{CustomTransformerAdditionalData, ExpressionDefinition, ObjectProcessDefinition, ProcessDefinition}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.types.EspTypeUtils

import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends FlatSpec with Matchers with Inside {

  import spel.Implicits._

  private def emptyQueryNamesData(clearsContext: Boolean = false) = CustomTransformerAdditionalData(Set(), clearsContext)

  private val baseDefinition = ProcessDefinition[ObjectDefinition](
    Map("sampleEnricher" -> ObjectDefinition(List.empty, classOf[SimpleRecord], List()), "withParamsService" -> ObjectDefinition(List(Parameter("par1",
      ClazzRef(classOf[String]))), classOf[SimpleRecord], List())),
    Map("source" -> ObjectDefinition(List.empty, classOf[SimpleRecord], List())),
    Map("sink" -> ObjectDefinition.noParam),

    Map("customTransformer" -> (ObjectDefinition(List.empty, classOf[SimpleRecord], List()), emptyQueryNamesData()),
      "withParamsTransformer" -> (ObjectDefinition(List(Parameter("par1", ClazzRef(classOf[String]))), classOf[SimpleRecord], List()), emptyQueryNamesData()),
      "clearingContextTransformer" -> (ObjectDefinition(List.empty, classOf[SimpleRecord], List()), emptyQueryNamesData(true)),
      "withoutReturnType" -> (ObjectDefinition(List(Parameter("par1", ClazzRef(classOf[String]))), classOf[Void], List()), emptyQueryNamesData())
    ),
    Map.empty,
    ObjectDefinition.noParam,
    ExpressionDefinition(
      Map("processHelper" -> ObjectDefinition(List(), ClazzRef(ProcessHelper.getClass), List("cat1"))),
      List.empty, optimizeCompilation = false
    ),
    EspTypeUtils.clazzAndItsChildrenDefinition(List(classOf[SampleEnricher], classOf[SimpleRecord], ProcessHelper.getClass))(ClassExtractionSettings.Default)
  )

  it should "validated with success" in {
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
        "funkySpelVariable" -> "(#input.list.?[plainValue == 5]).![plainValue].contains(5)"
      )
      .sink("id2", "#processHelper.add(#processHelper, 2)", "sink")
    ProcessValidator.default(baseDefinition).validate(correctProcess) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find duplicated ids" in {
    val duplicatedId = "id1"
    val processWithDuplicatedIds = EspProcessBuilder.id("process1").exceptionHandler().source(duplicatedId, "source").emptySink(duplicatedId, "sink")
    ProcessValidator.default(baseDefinition).validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  it should "find duplicated ids in switch" in {
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
    ProcessValidator.default(baseDefinition).validate(processWithDuplicatedIds) should matchPattern {
      case Invalid(NonEmptyList(DuplicatedNodeIds(_), _)) =>
    }
  }

  it should "find expression parse error" in {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .sink("id2", "wtf!!!", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError(_, _, _, _), _)) =>
    }
  }

  it should "find missing service error" in {
    val missingServiceId = "missingServiceId"
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("customNodeId", "event", "customTransformer")
        .processor("id2", missingServiceId, "foo" -> "'bar'")
        .sink("sink", "#event.plainValue", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingService(_, _), _)) =>
    }

    val validDefinition = baseDefinition.withService(missingServiceId, Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(validDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "find redundant service parameters" in {
    val serviceId = "serviceId"
    val definition = baseDefinition.withService(serviceId)

    val redundantServiceParameter = "foo"

    val processWithInvalidServiceInvocation =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .processorEnd("id2", serviceId, redundantServiceParameter -> "'bar'")

    ProcessValidator.default(definition).validate(processWithInvalidServiceInvocation) should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(_, _), _)) =>
    }
  }

  it should "find missing source" in {
    val serviceId = "serviceId"
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .filter("filter", "#input != null")
        .processorEnd("id2", serviceId, "foo" -> "'bar'")

    val definition = ObjectProcessDefinition.empty.withService(serviceId, Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(definition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference input", "filter", None, "#input != null"), List(MissingSourceFactory(_, _)))) =>
    }
  }


  it should "find missing custom node" in {
    val processWithRefToMissingService =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "out", "notExisting", "dummy" -> "input")
        .filter("filter", "#out != null")
        .emptySink("id2", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingCustomNodeExecutor("notExisting", "custom"), Nil)) =>
    }
  }

  it should "find missing parameter for exception handler" in {
    val process = EspProcessBuilder.id("process1").exceptionHandler().source("id1", "source").emptySink("id2", "sink")
    val definition = baseDefinition.withExceptionHandlerFactory(Parameter(name = "foo", typ = ClazzRef(classOf[String])))
    ProcessValidator.default(definition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(_, _), _)) =>
    }
  }

  it should "find usage of unresolved plain variables" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildVariable("bv1", "doesExist", "v1" -> "42")
      .filter("sampleFilter", "#doesExist['v1'] + #doesNotExist1 + #doesNotExist2 > 10")
      .emptySink("id2", "sink")
    ProcessValidator.default(baseDefinition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParseError("Unresolved reference doesNotExist1", "sampleFilter", None, _),
      List(ExpressionParseError("Unresolved reference doesNotExist2", "sampleFilter", None, _)))) =>
    }
  }

  it should "find usage of non references" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValue > 10")
      .filter("sampleFilter2", "input.plainValue > 10")
      .emptySink("id2", "sink")
    ProcessValidator.default(baseDefinition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Non reference 'input' occurred. Maybe you missed '#' in front of it?", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "find usage of fields that does not exist in object" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.value1.value2 > 10")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .emptySink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }


  it should "find not existing variables after custom node" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "find not existing variables after split" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .split("split1",
        GraphBuilder.emptySink("id2", "sink"),
        GraphBuilder.filter("sampleFilter2", "#input.value1.value3 > 10").emptySink("id3", "sink")
      )

    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "validate custom node return type" in {
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

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type 'pl.touk.nussknacker.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'",
      "sampleFilter2", None, "#out1.terefere"), _)) =>

    }
  }

  //TODO: implement validation to make it test fail
  it should "allow unknown vars in custom node params" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "#strangeVar")
      .emptySink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Valid(_) =>
    }

  }

  it should "pass custom node output var" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "#strangeVar")
      .sink("id2", "#out1", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Valid(_) =>
    }

  }


  it should "validate exception handler params" in {

    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .emptySink("id2", "sink")
    val definitionWithExceptionHandlerWithParams = baseDefinition.copy(exceptionHandlerFactory =
      ObjectDefinition.withParams(List(Parameter("param1", ClazzRef(classOf[String])))))

    inside (ProcessValidator.default(definitionWithExceptionHandlerWithParams).validate(process)) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "$process"), _)) => missingParam shouldBe Set("param1")
    }
  }



  it should "not validate exception handler params in subprocess" in {

    val subprocess = CanonicalProcess(MetaData("subProcess1", StreamMetaData(), true), ExceptionHandlerRef(List()),
      List(
        canonicalnode.FlatNode(SubprocessInputDefinition("start", List(DefinitionExtractor.Parameter("param", ClazzRef[String])))),
        canonicalnode.FlatNode(Sink("deadEnd", SinkRef("sink", List()), Some("'deadEnd'")))))

    val definitionWithExceptionHandlerWithParams = baseDefinition.copy(exceptionHandlerFactory =
      ObjectDefinition.withParams(List(Parameter("param1", ClazzRef(classOf[String])))))

    ProcessValidator.default(definitionWithExceptionHandlerWithParams).validate(subprocess) shouldBe 'valid
  }


  it should "validate service params" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .enricher("enricher1", "out", "withParamsService")
      .emptySink("id2", "sink")

    inside (ProcessValidator.default(definitionWithTypedSource).validate(process)) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "enricher1"), _)) => missingParam shouldBe Set("par1")
    }
  }

  private val definitionWithTypedSource = baseDefinition.copy(sourceFactories
    = Map("source" -> ObjectDefinition.noParam.copy(returnType = ClazzRef(classOf[SimpleRecord]))))

  private val definitionWithTypedSourceAndTransformNode =
    definitionWithTypedSource.withCustomStreamTransformer("custom",
      classOf[AnotherSimpleRecord], emptyQueryNamesData(), Parameter("par1", ClazzRef(classOf[String])))

  it should "find usage of fields that does not exist in option object" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValueOpt.terefere > 10")
      .emptySink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type 'scala.math.BigDecimal'", "sampleFilter1", None, _), _)) =>
    }
  }

  it should "return field/property names in errors" in {
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

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParseError("Unresolved reference terefere", "p1", Some("par1"), _),
      List(
      ExpressionParseError("Expression [{] @0: EL1044E: Unexpectedly ran out of input", "c1", Some("par1"), _),
      ExpressionParseError("Unresolved reference terefere22", "v1", Some("par1"), _))
      )) =>
    }
  }

  it should "not allow to overwrite variable by variable node" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .buildSimpleVariable("var1overwrite", "var1", "''")
      .emptySink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
    }
  }

  it should "not allow to overwrite variable by switch node" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .switch("var1overwrite", "''", "var1", GraphBuilder.emptySink("id2", "sink"))

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
    }
  }

  it should "not allow to overwrite variable by enricher node" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .enricher("var1overwrite", "var1", "sampleEnricher")
      .emptySink("id2", "sink")

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
    }
  }

  it should "not allow to overwrite variable by variable builder" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .buildVariable("var1overwrite", "var1", "a" -> "''")
      .emptySink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
    }
  }

  it should "not allow to overwrite variable by custom node" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .buildSimpleVariable("var1", "var1", "''")
      .customNode("var1overwrite", "var1", "custom", "par1" -> "''")
      .emptySink("id2", "sink")

    ProcessValidator.default(definitionWithTypedSourceAndTransformNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(OverwrittenVariable("var1", "var1overwrite"), _)) =>
    }
  }

  it should "allow different vars in branches" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .switch("switch", "''", "var2",
        GraphBuilder.buildSimpleVariable("var3", "var3", "''").emptySink("id2", "sink"),
         Case("true", GraphBuilder.buildSimpleVariable("var3b", "var3", "''").emptySink("id3", "sink")))

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Valid(_) =>
    }
  }

  it should "not allow to use vars from different branches" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .switch("switch", "''", "var2",
        GraphBuilder.buildSimpleVariable("var3", "var3", "''").emptySink("id2", "sink"),
         Case("false", GraphBuilder.sink("id3", "#var3", "sink")))

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference var3", "id3", None, "#var3"), _)) =>
    }
  }

  it should "not allow customNode outputVar when no return type in definition" in {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "varName", "withoutReturnType", "par1" -> "'1'")
        .sink("id2", "''", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(NonEmptyList(RedundantParameters(vars, _), _)) if vars == Set("OutputVariable") =>
    }
  }

  it should "detect clearing context in custom transformer" in {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNode("custom", "varName", "clearingContextTransformer")
        .sink("id2", "#input.toString()", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference input", "id2", None, "#input.toString()"), _)) =>
    }
  }


  it should "require customNode outputVar when return type in definition" in {
    val processWithInvalidExpresssion =
      EspProcessBuilder
        .id("process1")
        .exceptionHandler()
        .source("id1", "source")
        .customNodeNoOutput("custom", "customTransformer", "par1" -> "'1'")
        .sink("id2", "''", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithInvalidExpresssion) should matchPattern {
      case Invalid(NonEmptyList(MissingParameters(vars, _), _)) if vars == Set("OutputVariable") =>
    }
  }



  case class SimpleRecord(value1: AnotherSimpleRecord, plainValue: BigDecimal, plainValueOpt: Option[BigDecimal], intAsAny: Any, list: java.util.List[SimpleRecord]) {
    private val privateValue = "priv"

    def invoke1: Future[AnotherSimpleRecord] = ???

    def invoke2: State[ContextWithLazyValuesProvider, AnotherSimpleRecord] = ???

    def someMethod(a: Int): Int = ???
  }

  case class AnotherSimpleRecord(value2: Long)

  import scala.collection.JavaConversions._

  class SampleEnricher extends Service {
    def invoke()(implicit ec: ExecutionContext) = Future(SimpleRecord(AnotherSimpleRecord(1), 2, Option(2), 1, List.empty[SimpleRecord]))
  }

  object ProcessHelper {
    def add(a: Int, b: Int) = a + b
  }

}