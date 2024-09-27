package pl.touk.nussknacker.engine.compile

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, Validated}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.{Inside, OptionValues}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{
  EmptyMandatoryParameter,
  ExpressionParserCompilationError,
  WrongParameters
}
import pl.touk.nussknacker.engine.api.definition.{DualParameterEditor, Parameter, StringParameterEditor}
import pl.touk.nussknacker.engine.api.editor.DualEditorMode
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.validationHelpers._
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.component.parameter.editor.ParameterTypeEditorDeterminer
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.CustomProcessValidatorLoader
import pl.touk.nussknacker.engine.api.typed.typing.Typed.typedListWithElementValues
import scala.jdk.CollectionConverters._

import scala.jdk.CollectionConverters._

class GenericTransformationValidationSpec extends AnyFunSuite with Matchers with OptionValues with Inside {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val components = List(
    ComponentDefinition("genericParameters", GenericParametersTransformer),
    ComponentDefinition("genericJoin", DynamicParameterJoinTransformer),
    ComponentDefinition("twoStepsInOne", GenericParametersTransformerWithTwoStepsThatCanBeDoneInOneStep),
    ComponentDefinition("paramsLoop", ParamsLoopNode),
    ComponentDefinition("mySource", SimpleStringSource),
    ComponentDefinition("genericParametersSource", new GenericParametersSource),
    ComponentDefinition("dummySink", SinkFactory.noParam(new Sink {})),
    ComponentDefinition("genericParametersSink", GenericParametersSink),
    ComponentDefinition("optionalParametersSink", OptionalParametersSink),
    ComponentDefinition("genericParametersProcessor", GenericParametersProcessor),
    ComponentDefinition("genericParametersEnricher", GenericParametersEnricher),
    ComponentDefinition("genericParametersThrowingException", GenericParametersThrowingException),
  )

  private val processBase = ScenarioBuilder.streaming("proc1").source("sourceId", "mySource")

  private val modelDefinition = ModelDefinition(
    ComponentDefinitionWithImplementation
      .forList(components, ComponentsUiConfig.Empty, id => DesignerWideComponentId(id.toString), Map.empty),
    ModelDefinitionBuilder.emptyExpressionConfig,
    ClassExtractionSettings.Default
  )

  private val validator = ProcessValidator.default(
    ModelDefinitionWithClasses(modelDefinition),
    new SimpleDictRegistry(Map.empty),
    CustomProcessValidatorLoader.emptyCustomProcessValidator
  )

  private def validate(process: CanonicalProcess) = {
    implicit val jobData: JobData =
      JobData(process.metaData, ProcessVersion.empty.copy(processName = process.metaData.name))
    validator.validate(process, isFragment = false)
  }

  private val expectedGenericParameters = List(
    Parameter[String](ParameterName("par1"))
      .copy(
        editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
        defaultValue = Some("''".spel)
      ),
    Parameter[Long](ParameterName("lazyPar1")).copy(isLazyParameter = true, defaultValue = Some("0".spel)),
    Parameter(ParameterName("val1"), Unknown),
    Parameter(ParameterName("val2"), Unknown),
    Parameter(ParameterName("val3"), Unknown)
  )

  test("should validate happy path") {
    val result = validate(
      processBase
        .customNode(
          "generic",
          "out1",
          "genericParameters",
          "par1"     -> "'val1,val2,val3'".spel,
          "lazyPar1" -> "#input == null ? 1 : 5".spel,
          "val1"     -> "'aa'".spel,
          "val2"     -> "11".spel,
          "val3"     -> "{false}".spel
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Symbol("valid")
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe Typed.record(
      Map(
        "val1" -> Typed.fromInstance("aa"),
        "val2" -> Typed.fromInstance(11),
        "val3" -> typedListWithElementValues(Typed[java.lang.Boolean], List(false).asJava)
      )
    )

    result.parametersInNodes("generic") shouldBe expectedGenericParameters
  }

  test("should validate sources") {
    val result = validate(
      ScenarioBuilder
        .streaming("proc1")
        .source(
          "sourceId",
          "genericParametersSource",
          "par1"     -> "'val1,val2,val3'".spel,
          "lazyPar1" -> "'ll' == null ? 1 : 5".spel,
          "val1"     -> "'aa'".spel,
          "val2"     -> "11".spel,
          "val3"     -> "{false}".spel
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Symbol("valid")
    val info1 = result.typing("end")

    info1.inputValidationContext("otherNameThanInput") shouldBe Typed.record(
      Map(
        "val1" -> Typed.fromInstance("aa"),
        "val2" -> Typed.fromInstance(11),
        "val3" -> typedListWithElementValues(Typed[java.lang.Boolean], List(false).asJava)
      )
    )

    result.parametersInNodes("sourceId") shouldBe expectedGenericParameters
  }

  test("should validate sinks") {
    val result = validate(
      processBase.emptySink(
        "end",
        "genericParametersSink",
        "par1"     -> "'val1,val2,val3'".spel,
        "lazyPar1" -> "#input == null ? 1 : 5".spel,
        "val1"     -> "'aa'".spel,
        "val2"     -> "11".spel,
        "val3"     -> "{false}".spel
      )
    )
    result.result shouldBe Symbol("valid")

    result.parametersInNodes("end") shouldBe expectedGenericParameters
  }

  test("should validate services") {
    val result = validate(
      processBase
        .processor(
          "genericProcessor",
          "genericParametersProcessor",
          "par1"     -> "'val1,val2,val3'".spel,
          "lazyPar1" -> "#input == null ? 1 : 5".spel,
          "val1"     -> "'aa'".spel,
          "val2"     -> "11".spel,
          "val3"     -> "{false}".spel
        )
        .enricher(
          "genericEnricher",
          "out",
          "genericParametersProcessor",
          "par1"     -> "'val1,val2,val3'".spel,
          "lazyPar1" -> "#input == null ? 1 : 5".spel,
          "val1"     -> "'aa'".spel,
          "val2"     -> "11".spel,
          "val3"     -> "{false}".spel
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Symbol("valid")

    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
  }

  test("should handle exception throws during validation gracefully") {
    val result = validate(
      processBase
        .processor(
          "genericProcessor",
          "genericParametersThrowingException",
          "par1"     -> "'val1,val2,val3'".spel,
          "lazyPar1" -> "#input == null ? 1 : 5".spel,
          "val1"     -> "'aa'".spel,
          "val2"     -> "11".spel,
          "val3"     -> "{false}".spel
        )
        .emptySink("end", "dummySink")
    )

    result.parametersInNodes("genericProcessor") shouldBe expectedGenericParameters
  }

  test("should dependent parameter in sink") {
    val result = validate(
      processBase.emptySink(
        "end",
        "genericParametersSink",
        "par1"     -> "'val1,val2'".spel,
        "lazyPar1" -> "#input == null ? 1 : 5".spel,
        "val1"     -> "''".spel
      )
    )
    result.result should matchPattern {
      case Invalid(NonEmptyList(EmptyMandatoryParameter(_, _, ParameterName("val2"), "end"), Nil)) =>
    }

    val parameters = result.parametersInNodes("end")
    parameters shouldBe List(
      Parameter[String](ParameterName("par1"))
        .copy(
          editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
          defaultValue = Some("''".spel)
        ),
      Parameter[Long](ParameterName("lazyPar1")).copy(isLazyParameter = true, defaultValue = Some("0".spel)),
      Parameter(ParameterName("val1"), Unknown),
      Parameter(ParameterName("val2"), Unknown)
    )
  }

  test("should find wrong determining parameter") {

    val result = validate(
      processBase
        .customNode(
          "generic",
          "out1",
          "genericParameters",
          "par1"     -> "12".spel,
          "lazyPar1" -> "#input == null ? 1 : 5".spel
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(
      NonEmptyList.of(
        ExpressionParserCompilationError(
          message = s"Bad expression type, expected: String, found: ${Typed.fromInstance(12).display}",
          nodeId = "generic",
          paramName = Some(ParameterName("par1")),
          originalExpr = "12",
          details = None
        )
      )
    )
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe Typed.record(Map.empty[String, TypingResult])

  }

  test("should find wrong dependent parameters") {

    val result = validate(
      processBase
        .customNode(
          "generic",
          "out1",
          "genericParameters",
          "par1"     -> "'val1,val2'".spel,
          "lazyPar1" -> "#input == null ? 1 : 5".spel,
          "val1"     -> "''".spel
        )
        .emptySink("end", "dummySink")
    )
    result.result should matchPattern {
      case Invalid(NonEmptyList(EmptyMandatoryParameter(_, _, ParameterName("val2"), "generic"), Nil)) =>
    }

    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe Typed.record(
      Map(
        "val1" -> Typed.fromInstance(""),
        "val2" -> Unknown
      )
    )

    val parameters = result.parametersInNodes("generic")
    parameters shouldBe List(
      Parameter[String](ParameterName("par1"))
        .copy(
          editor = Some(DualParameterEditor(StringParameterEditor, DualEditorMode.RAW)),
          defaultValue = Some("''".spel)
        ),
      Parameter[Long](ParameterName("lazyPar1")).copy(isLazyParameter = true, defaultValue = Some("0".spel)),
      Parameter(ParameterName("val1"), Unknown),
      Parameter(ParameterName("val2"), Unknown)
    )
  }

  test("should find no output variable") {

    val result = validate(
      processBase
        .customNode(
          "generic",
          "out1",
          "genericParameters",
          "par1"     -> "12".spel,
          "lazyPar1" -> "#input == null ? 1 : 5".spel
        )
        .emptySink("end", "dummySink")
    )
    result.result shouldBe Invalid(
      NonEmptyList.of(
        ExpressionParserCompilationError(
          message = s"Bad expression type, expected: String, found: ${Typed.fromInstance(12).display}",
          nodeId = "generic",
          paramName = Some(ParameterName("par1")),
          originalExpr = "12",
          details = None
        )
      )
    )
    val info1 = result.typing("end")

    info1.inputValidationContext("out1") shouldBe Typed.record(Map.empty[String, TypingResult])
  }

  test("should compute dynamic parameters in joins") {

    val process = ScenarioBuilder
      .streaming("proc1")
      .sources(
        GraphBuilder
          .source("sourceId1", "mySource")
          .buildSimpleVariable("var1", "intVal", "123".spel)
          .branchEnd("branch1", "join1"),
        GraphBuilder
          .source("sourceId2", "mySource")
          .buildSimpleVariable("var2", "strVal", "'abc'".spel)
          .branchEnd("branch2", "join1"),
        GraphBuilder
          .join(
            "join1",
            "genericJoin",
            Some("outPutVar"),
            List(
              "branch1" -> List("isLeft" -> "true".spel),
              "branch2" -> List("isLeft" -> "false".spel)
            ),
            "rightValue" -> "#strVal + 'dd'".spel
          )
          .emptySink("end", "dummySink")
      )
    val validationResult = validate(process)

    val varsInEnd = validationResult.variablesInNodes("end")
    varsInEnd("outPutVar") shouldBe Typed.fromInstance("abcdd")
    varsInEnd("intVal") shouldBe Typed.fromInstance(123)
    varsInEnd.get("strVal") shouldBe None
  }

  test("should validate optional parameter default value") {
    val process = processBase
      .emptySink("optionalParameters", "optionalParametersSink", "wrongOptionalParameter" -> "'123'".spel)

    val result = validate(process)

    val parameters = result.parametersInNodes("optionalParameters")
    parameters shouldBe List(
      Parameter
        .optional[CharSequence](ParameterName("optionalParameter"))
        .copy(editor = new ParameterTypeEditorDeterminer(Typed[CharSequence]).determine(), defaultValue = Some("".spel))
    )
  }

  test("should be possible to perform two steps of validation in one step using defaults as node parameters") {
    val result = validate(
      processBase
        .customNodeNoOutput("generic", "twoStepsInOne")
        .emptySink("end", "dummySink")
    )

    result.result shouldBe Symbol("valid")
    val parameterNames = result.parametersInNodes("generic").map(_.name)
    parameterNames shouldEqual List(ParameterName("moreParams"), ParameterName("extraParam"))
  }

  test("should omit redundant parameters for generic transformations") {
    val result = validate(
      processBase
        .customNodeNoOutput("generic", "twoStepsInOne", "redundant" -> "''".spel)
        .emptySink("end", "dummySink")
    )

    result.result shouldBe Symbol("valid")
    val parameterNames = result.parametersInNodes("generic").map(_.name)
    parameterNames shouldEqual List(ParameterName("moreParams"), ParameterName("extraParam"))
  }

  test("should not fall in endless loop for buggy node implementation") {
    val result = validate(
      processBase
        .customNodeNoOutput("generic", "paramsLoop")
        .emptySink("end", "dummySink")
    )

    result.result shouldBe Validated.invalidNel(WrongParameters(Set.empty, Set.empty)(NodeId("generic")))
  }

}
