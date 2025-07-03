package pl.touk.nussknacker.engine.language.json

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.language.json.JsonParser.JsonParseError
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import scala.jdk.CollectionConverters._
import scala.reflect.runtime.universe._

class JsonTemplateParserTest extends AnyFunSuite with Matchers with EitherValues with TableDrivenPropertyChecks {

  private val spelTemplateParser = SpelExpressionParser.default(
    getClass.getClassLoader,
    ModelDefinitionBuilder.emptyExpressionConfig,
    new SimpleDictRegistry(Map.empty),
    enableSpelForceCompile = false,
    SpelExpressionParser.Template,
    ClassDefinitionTestUtils.createDefinitionWithDefaultsAndExtensions,
  )

  private val spelParser = SpelExpressionParser.default(
    getClass.getClassLoader,
    ModelDefinitionBuilder.emptyExpressionConfig,
    new SimpleDictRegistry(Map.empty),
    enableSpelForceCompile = false,
    SpelExpressionParser.Standard,
    ClassDefinitionTestUtils.createDefinitionWithDefaultsAndExtensions,
  )

  private val sut = new JsonTemplateParser(spelTemplateParser, spelParser)

  private val ctxWithVariables = ValidationContext(
    Map(
      "name"       -> Typed.typedClass[String],
      "age"        -> Typed.typedClass[Long],
      "hasConsent" -> Typed.typedClass[Boolean],
      "amount"     -> Typed.typedClass[java.math.BigDecimal],
    ),
    Map.empty,
    None
  )

  private val evaluationContext = Context.dummy.withVariables(
    Map(
      "name"       -> "John",
      "age"        -> 50,
      "hasConsent" -> true,
      "amount"     -> 100.5,
    )
  )

  test("should parse json templates") {
    forAll(
      Table(
        ("Data sample", "Typing result"),
        ("", Typed.json),
        ("{}", Typed.record(List())),
        ("123", Typed.typedClass[Integer]),
        ("[]", Typed.genericTypeClass[java.util.List[_]](List(Typed.json))),
        ("\"text\"", Typed.typedClass[String]),
        ("false", Typed.typedClass[java.lang.Boolean]),
        ("null", Typed.json),
        (
          s"""
           |{
           |  "name": "Tom",
           |  "age": 22,
           |  "city": "Warsaw"
           |}
           |""".stripMargin,
          Typed.record(
            List(
              "name" -> Typed.typedClass[String],
              "age"  -> Typed.typedClass[Integer],
              "city" -> Typed.typedClass[String],
            )
          )
        ),
        (
          s"""
           |[
           |  {
           |    "name": "Tom",
           |    "age": 22,
           |    "city": "Warsaw"
           |  }
           |]
           |""".stripMargin,
          Typed.genericTypeClass[java.util.List[_]](
            List(
              Typed.record(
                List(
                  "name" -> Typed.typedClass[String],
                  "age"  -> Typed.typedClass[Integer],
                  "city" -> Typed.typedClass[String],
                )
              )
            )
          )
        ),
        (
          s"""
           |{
           |  "name": "#{ #name }",
           |  "age": #{ #age },
           |  "hasConsent": #{ #hasConsent },
           |  "amount": #{ #amount }
           |}""".stripMargin,
          Typed.record(
            List(
              "name"       -> Typed.typedClass[String],
              "age"        -> Typed.typedClass[Integer],
              "hasConsent" -> Typed.typedClass[java.lang.Boolean],
              "amount"     -> Typed.typedClass[java.math.BigDecimal],
            )
          )
        ),
      )
    ) { (dataSample: String, typingResult: TypingResult) =>
      parse[Any](dataSample, ctxWithVariables).map(_.returnType) shouldBe Valid(typingResult)
      parseWithoutContextValidation[String](dataSample) shouldBe Symbol("valid")
    }
  }

  test("should evaluate json template") {
    val dataSample =
      s"""{
         |  "name": "#{ #name }",
         |  "age": #{ #age },
         |  "hasConsent": #{ #hasConsent },
         |  "amount": #{ #amount }
         |}""".stripMargin

    val mapResult = parse[Any](dataSample, ctxWithVariables).validValue.expression
      .evaluate[Any](evaluationContext, Map.empty)

    mapResult shouldBe Map(
      "name"       -> "John",
      "age"        -> 50,
      "hasConsent" -> true,
      "amount"     -> new java.math.BigDecimal("100.5"),
    ).asJava
  }

  test("should return error when JSON cannot be parsed") {
    val invalidJson = """|{
                         |  "products": [
                         |}""".stripMargin

    val parsingErrors = parse[String](invalidJson).invalidValue

    parsingErrors shouldBe NonEmptyList.of(
      JsonParseError(
        "expected json value got '}'",
        Some(CoordinatesBasedTextRange(TextCoordinates(0, 2), TextCoordinates(1, 2)))
      )
    )
  }

  test("should allow to use Json type in field values where non-string value is expected") {
    val jsonWithExpressionPlaceholderInUnquotedFieldValue =
      """{
        |  "field1": #{ #field1Value }
        |}""".stripMargin
    val validationContext = ValidationContext.empty.withVariableUnsafe("field1Value", Typed.json)
    val typedExpression   = parse[Any](jsonWithExpressionPlaceholderInUnquotedFieldValue, validationContext).validValue
    typedExpression.typingInfo.typingResult shouldBe Typed.record(Seq("field1" -> Typed.json))

    forAll(
      Table(
        ("test case", "field1Value"),
        ("integer value", 1),
        ("boolean value", true),
        ("list value", List(1, 2, 3).asJava),
        ("record value", Map("field2" -> 123).asJava),
      )
    ) { (_, field1Value) =>
      typedExpression.expression
        .evaluate[java.util.Map[String, Any]](
          Context.dummy.withVariable("field1Value", field1Value),
          globals = Map.empty
        ) shouldBe Map("field1" -> field1Value).asJava
    }
  }

  test("should allow to use Json type in list elements") {
    val jsonWithExpressionPlaceholderInUnquotedFieldValue =
      """[
        |  #{ #listElement }
        |]""".stripMargin
    val validationContext = ValidationContext.empty.withVariableUnsafe("listElement", Typed.json)
    val typedExpression   = parse[Any](jsonWithExpressionPlaceholderInUnquotedFieldValue, validationContext).validValue
    typedExpression.typingInfo.typingResult shouldBe Typed.genericTypeClass(
      classOf[java.util.List[_]],
      List(Typed.json)
    )

    forAll(
      Table(
        ("test case", "listElement"),
        ("integer value", 1),
        ("boolean value", true),
        ("list value", List(1, 2, 3).asJava),
        ("record value", Map("field2" -> 123).asJava),
      )
    ) { (_, listElement) =>
      typedExpression.expression
        .evaluate[java.util.List[Any]](
          Context.dummy.withVariable("listElement", listElement),
          globals = Map.empty
        ) shouldBe List(listElement).asJava
    }
  }

  // FIXME abr: test showing list with both known elements and unknown elements

  private def parse[T: TypeTag](
      jsonString: String,
      ctx: ValidationContext = ValidationContext.empty,
  ): Validated[NonEmptyList[ExpressionParseError], TypedExpression] = {
    sut.parse(jsonString, ctx, Typed.fromDetailedType[T])
  }

  private def parseWithoutContextValidation[T: TypeTag](
      jsonString: String
  ): Validated[NonEmptyList[ExpressionParseError], CompiledExpression] = {
    sut.parseWithoutContextValidation(jsonString, Typed.fromDetailedType[T])
  }

}
