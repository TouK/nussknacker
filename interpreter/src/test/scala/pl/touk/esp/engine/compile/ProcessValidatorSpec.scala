package pl.touk.esp.engine.compile

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.esp.engine._
import pl.touk.esp.engine.api.Service
import pl.touk.esp.engine.api.lazyy.ContextWithLazyValuesProvider
import pl.touk.esp.engine.api.process.WithCategories
import pl.touk.esp.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.esp.engine.compile.ProcessCompilationError._
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, ObjectDefinition, Parameter}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.esp.engine.graph.node.Case
import pl.touk.esp.engine.types.EspTypeUtils

import scala.concurrent.{ExecutionContext, Future}

class ProcessValidatorSpec extends FlatSpec with Matchers with Inside {

  import spel.Implicits._

  private val baseDefinition = ProcessDefinition(
    Map("sampleEnricher" -> ObjectDefinition(List.empty, classOf[SimpleRecord], List()), "withParamsService" -> ObjectDefinition(List(Parameter("par1",
      ClazzRef(classOf[String]))), classOf[SimpleRecord], List())),
    Map("source" -> ObjectDefinition(List.empty, classOf[SimpleRecord], List())),
    Map("sink" -> ObjectDefinition.noParam),
    Map("customTransformer" -> (ObjectDefinition(List.empty, classOf[SimpleRecord], List()), Set.empty[String]),
      "withParamsTransformer" -> (ObjectDefinition(List(Parameter("par1", ClazzRef(classOf[String]))), classOf[SimpleRecord], List()), Set.empty[String]),
      "withoutReturnType" -> (ObjectDefinition(List(Parameter("par1", ClazzRef(classOf[String]))), classOf[Void], List()), Set.empty[String])
    ),
    Map.empty,
    ObjectDefinition.noParam,
    Map("processHelper" -> WithCategories(ClazzRef(ProcessHelper.getClass), List("cat1"))),
    EspTypeUtils.clazzAndItsChildrenDefinition(List(classOf[SampleEnricher], classOf[SimpleRecord], ProcessHelper.getClass))
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
    val processWithDuplicatedIds = EspProcessBuilder.id("process1").exceptionHandler().source(duplicatedId, "source").sink(duplicatedId, "sink")
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
          Case("'1'", GraphBuilder.sink(duplicatedId, "sink")),
          Case("'2'", GraphBuilder.sink(duplicatedId, "sink"))
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
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved references input", "filter", None, "#input != null"), List(MissingSourceFactory(_, _)))) =>
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
        .sink("id2", "sink")

    ProcessValidator.default(baseDefinition).validate(processWithRefToMissingService) should matchPattern {
      case Invalid(NonEmptyList(MissingCustomNodeExecutor("notExisting", "custom"), Nil)) =>
    }
  }

  it should "find missing parameter for exception handler" in {
    val process = EspProcessBuilder.id("process1").exceptionHandler().source("id1", "source").sink("id2", "sink")
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
      .sink("id2", "sink")
    ProcessValidator.default(baseDefinition).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved references doesNotExist1, doesNotExist2", "sampleFilter", None, _), _)) =>
    }
  }

  it should "find usage of non references" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValue > 10")
      .filter("sampleFilter2", "input.plainValue > 10")
      .sink("id2", "sink")
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
      .sink("id2", "sink")
    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }


  it should "find not existing variables after custom node" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter2", "#input.value1.value3 > 10")
      .sink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "find not existing variables after split" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .split("split1",
        GraphBuilder.sink("id2", "sink"),
        GraphBuilder.filter("sampleFilter2", "#input.value1.value3 > 10").sink("id3", "sink")
      )

    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'value3' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'", "sampleFilter2", None, _), _)) =>
    }
  }

  it should "validate custom node return type" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNodeNoOutput("noOutput", "withoutReturnType", "par1" -> "'1'")
      .customNode("cNode1", "out1", "custom", "par1" -> "'1'")
      .filter("sampleFilter1", "#out1.value2")
      .filter("sampleFilter2", "#out1.terefere")
      .sink("id2", "sink")
    val definitionWithCustomNode = definitionWithTypedSourceAndTransformNode

    ProcessValidator.default(definitionWithCustomNode).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("There is no property 'terefere' in type 'pl.touk.esp.engine.compile.ProcessValidatorSpec$AnotherSimpleRecord'",
      "sampleFilter2", None, "#out1.terefere"), _)) =>

    }
  }

  //TODO: zrobic cos zeby dalo sie jednak cos tu walidowac...
  it should "allow unknown vars in custom node params" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .customNode("cNode1", "out1", "custom", "par1" -> "#strangeVar")
      .sink("id2", "sink")
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
      .sink("id2", "sink")
    val definitionWithExceptionHandlerWithParams = baseDefinition.copy(exceptionHandlerFactory =
      ObjectDefinition.withParams(List(Parameter("param1", ClazzRef(classOf[String])))))

    inside (ProcessValidator.default(definitionWithExceptionHandlerWithParams).validate(process)) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "$process"), _)) => missingParam shouldBe Set("param1")
    }
  }

  it should "validate service params" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .enricher("enricher1", "out", "withParamsService")
      .sink("id2", "sink")

    inside (ProcessValidator.default(definitionWithTypedSource).validate(process)) {
      case Invalid(NonEmptyList(MissingParameters(missingParam, "enricher1"), _)) => missingParam shouldBe Set("par1")
    }
  }

  private val definitionWithTypedSource = baseDefinition.copy(sourceFactories
    = Map("source" -> ObjectDefinition.noParam.copy(returnType = ClazzRef(classOf[SimpleRecord]))))

  private val definitionWithTypedSourceAndTransformNode =
    definitionWithTypedSource.withCustomStreamTransformer("custom", classOf[AnotherSimpleRecord], Set.empty, Parameter("par1", ClazzRef(classOf[String])))

  it should "find usage of fields that does not exist in option object" in {
    val process = EspProcessBuilder
      .id("process1")
      .exceptionHandler()
      .source("id1", "source")
      .filter("sampleFilter1", "#input.plainValueOpt.terefere > 10")
      .sink("id2", "sink")
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
        GraphBuilder.customNode("c1", "output", "withParamsTransformer", "par1" -> "{").sink("id2", "sink"),
        GraphBuilder.buildVariable("v1", "output", "par1" -> "#terefere22")
          .sink("id3", "sink")
      )

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(
      ExpressionParseError("Unresolved references terefere", "p1", Some("par1"), _),
      List(
      ExpressionParseError("EL1044E:(pos 0): Unexpectedly ran out of input", "c1", Some("par1"), _),
      ExpressionParseError("Unresolved references terefere22", "v1", Some("par1"), _))
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
      .sink("id2", "sink")
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
      .switch("var1overwrite", "''", "var1", GraphBuilder.sink("id2", "sink"))

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
      .sink("id2", "sink")

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
      .sink("id2", "sink")
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
      .sink("id2", "sink")

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
        GraphBuilder.buildSimpleVariable("var3", "var3", "''").sink("id2", "sink"),
         Case("''", GraphBuilder.buildSimpleVariable("var3b", "var3", "''").sink("id3", "sink")))

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
        GraphBuilder.buildSimpleVariable("var3", "var3", "''").sink("id2", "sink"),
         Case("''", GraphBuilder.sink("id3", "#var3", "sink")))

    ProcessValidator.default(definitionWithTypedSource).validate(process) should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved references var3", "id3", None, "#var3"), _)) =>
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