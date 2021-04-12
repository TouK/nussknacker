package pl.touk.nussknacker.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers, OptionValues}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{ExpressionParseError, MissingParameters}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{CustomStreamTransformer, MetaData, Service, StreamMetaData, process}
import pl.touk.nussknacker.engine.build.{EspProcessBuilder, GraphBuilder}
import pl.touk.nussknacker.engine.compile.validationHelpers._
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

class GenericTransformationValidationSpec extends FunSuite with Matchers with OptionValues {

  import spel.Implicits._

  object MyProcessConfigCreator extends EmptyProcessConfigCreator {
    override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map(
      "genericParameters" -> WithCategories(GenericParametersTransformer),
      "genericJoin" -> WithCategories(DynamicParameterJoinTransformer)
    )

    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory[_]]] = Map(
      "mySource" -> WithCategories(SimpleStringSource),
      "genericParametersSource" -> WithCategories(new GenericParametersSource)
    )

    override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
      "dummySink" -> WithCategories(SinkFactory.noParam(new Sink {
        override def testDataOutput: Option[Nothing] = None
      })),
      "genericParametersSink" -> WithCategories(GenericParametersSink)
    )

    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
      "genericParametersProcessor" -> WithCategories(GenericParametersProcessor),
      "genericParametersEnricher" -> WithCategories(GenericParametersEnricher)
    )
  }

  private val processBase = EspProcessBuilder.id("proc1").exceptionHandler().source("sourceId", "mySource")
  private val objectWithMethodDef = ProcessDefinitionExtractor.extractObjectWithMethods(MyProcessConfigCreator,
    process.ProcessObjectDependencies(ConfigFactory.empty, ObjectNamingProvider(getClass.getClassLoader)))
  private val validator = ProcessValidator.default(objectWithMethodDef, new SimpleDictRegistry(Map.empty))

  private val expectedGenericParameters = List(
    Parameter[String]("par1").copy(editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
    Parameter[Long]("lazyPar1").copy(isLazyParameter = true),
    Parameter("val1", Unknown),
    Parameter("val2", Unknown),
    Parameter("val3", Unknown)
  )


  test("should validate happy path") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",

          "par1" -> "'val1,val2,val3'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1" -> "'aa'",
          "val2" -> "11",
          "val3" -> "{false}"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe 'valid
    val info1 = result.typing("end")
    
    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map(
      "val1" -> Typed[String],
      "val2" -> Typed[java.lang.Integer],
      "val3" -> Typed.fromDetailedType[java.util.List[Boolean]]
    ))

    result.parametersInNodes("generic") shouldBe expectedGenericParameters


  }

  test("should validate sources") {
    val result = validator.validate(
      EspProcessBuilder.id("proc1").exceptionHandler().source("sourceId", "genericParametersSource",
           "par1" -> "'val1,val2,val3'",
           "lazyPar1" -> "'ll' == null ? 1 : 5",
           "val1" -> "'aa'",
           "val2" -> "11",
           "val3" -> "{false}"
         )
         .emptySink("end", "dummySink")
     )
     result.result shouldBe 'valid
     val info1 = result.typing("end")

     info1.inputValidationContext("otherNameThanInput") shouldBe TypedObjectTypingResult(Map(
       "val1" -> Typed[String],
       "val2" -> Typed[java.lang.Integer],
       "val3" -> Typed.fromDetailedType[java.util.List[Boolean]]
     ))

    result.parametersInNodes("sourceId") shouldBe expectedGenericParameters

  }

  test("should validate sinks") {
    val result = validator.validate(
      processBase.emptySink("end", "genericParametersSink",
           "par1" -> "'val1,val2,val3'",
           "lazyPar1" -> "#input == null ? 1 : 5",
           "val1" -> "'aa'",
           "val2" -> "11",
           "val3" -> "{false}"
         )
     )
     result.result shouldBe 'valid

    result.parametersInNodes("end") shouldBe expectedGenericParameters

  }

  test("should validate services") {
    val result = validator.validate(
    processBase.processor("genericProcessor", "genericParametersProcessor",
              "par1" -> "'val1,val2,val3'",
              "lazyPar1" -> "#input == null ? 1 : 5",
              "val1" -> "'aa'",
              "val2" -> "11",
              "val3" -> "{false}"
            ).enricher("genericEnricher", "out", "genericParametersProcessor",
                "par1" -> "'val1,val2,val3'",
                "lazyPar1" -> "#input == null ? 1 : 5",
                "val1" -> "'aa'",
                "val2" -> "11",
                "val3" -> "{false}"
              )
            .emptySink("end", "dummySink")
    )
    result.result shouldBe 'valid

    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
  }

  test("should dependent parameter in sink") {
    val result = validator.validate(
      processBase.emptySink("end", "genericParametersSink",
        "par1" -> "'val1,val2'",
        "lazyPar1" -> "#input == null ? 1 : 5",
        "val1" -> "''"
      )
    )
    result.result shouldBe Invalid(NonEmptyList.of(MissingParameters(Set("val2"), "end")))

    val parameters = result.parametersInNodes("end")
    parameters shouldBe List(
      Parameter[String]("par1").copy(editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
      Parameter[Long]("lazyPar1").copy(isLazyParameter = true),
      Parameter("val1", Unknown),
      Parameter("val2", Unknown)
    )
  }

  test("should find wrong determining parameter") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",
          "par1" -> "12",
          "lazyPar1" -> "#input == null ? 1 : 5"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(NonEmptyList.of(ExpressionParseError("Bad expression type, expected: String, found: Integer",
      "generic",Some("par1"),"12")))
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map.empty[String, TypingResult])

  }

  test("should find wrong dependent parameters") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",
          "par1" -> "'val1,val2'",
          "lazyPar1" -> "#input == null ? 1 : 5",
          "val1" -> "''"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(NonEmptyList.of(MissingParameters(Set("val2"), "generic")))
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map(
      "val1" -> Typed[String],
      "val2" -> Unknown
    ))

    val parameters = result.parametersInNodes("generic")
    parameters shouldBe List(
      Parameter[String]("par1").copy(editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW))),
      Parameter[Long]("lazyPar1").copy(isLazyParameter = true),
      Parameter("val1", Unknown),
      Parameter("val2", Unknown)
    )
  }


  test("should find no output variable") {

    val result = validator.validate(
      processBase
        .customNode("generic", "out1", "genericParameters",
          "par1" -> "12",
          "lazyPar1" -> "#input == null ? 1 : 5"
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(NonEmptyList.of(ExpressionParseError("Bad expression type, expected: String, found: Integer",
      "generic",Some("par1"),"12")))
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe TypedObjectTypingResult(Map.empty[String, TypingResult])
  }

  test("should compute dynamic parameters in joins") {

    val process =  EspProcess(MetaData("proc1", StreamMetaData()), ExceptionHandlerRef(List()), NonEmptyList.of(
        GraphBuilder
          .source("sourceId1", "mySource")
          .buildSimpleVariable("var1", "intVal", "123")
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .buildSimpleVariable("var2", "strVal", "'abc'")
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .branch("join1", "genericJoin", Some("outPutVar"),
            List(
              "branch1" -> List("isLeft" -> "true"),
              "branch2" -> List("isLeft" -> "false")
            ), "rightValue" -> "#strVal + 'dd'"
          )
          .emptySink("end", "dummySink")
      ))
    val validationResult = validator.validate(process)

    val varsInEnd = validationResult.variablesInNodes("end")
    varsInEnd("outPutVar") shouldBe Typed[String]
    varsInEnd("intVal") shouldBe Typed[Integer]
    varsInEnd.get("strVal") shouldBe None
  }


}
