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
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.spel.SpelExpressionParseError.JsonParsingError
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

  private val evaluationContext = Context("test").withVariables(
    Map(
      "name"       -> "John",
      "age"        -> 50,
      "hasConsent" -> true,
      "amount"     -> 100.0,
    )
  )

  test("should parse json templates") {
    forAll(
      Table(
        "{}",
        "123",
        "[]",
        "\"text\"",
        "false",
        "null",
        s"""
           |{
           |  "name": "Tom",
           |  "age": 22,
           |  "city": "Warsaw"
           |}
           |""".stripMargin,
        s"""
           |[
           |  {
           |    "name": "Tom",
           |    "age": 22,
           |    "city": "Warsaw"
           |  }
           |]
           |""".stripMargin,
        s"""
           |{
           |  "name": "#{#name}",
           |  "age": #{#age},
           |  "hasConsent": #{#hasConsent},
           |  "amount": #{#amount}
           |}""".stripMargin,
      )
    ) { dataSample: String =>
      parse[String](dataSample, ctxWithVariables).map(_.returnType) shouldBe Valid(Typed.typedClass[String])
      parseWithoutContextValidation[String](dataSample).isValid shouldBe true
    }
  }

  test("should evaluate json template to string") {
    val dataSample =
      s"""{
         |  "name": "#{#name}",
         |  "age": #{#age},
         |  "hasConsent": #{#hasConsent},
         |  "amount": #{#amount}
         |}""".stripMargin

    val result = parse[String](dataSample, ctxWithVariables).validValue.expression
      .evaluate[String](evaluationContext, Map.empty)

    result shouldBe
      """{
        |  "name": "John",
        |  "age": 50,
        |  "hasConsent": true,
        |  "amount": 100.0
        |}""".stripMargin
  }

  test("should treat complex variables as strings") {
    val jsonWithComplexVariables = """{
                                     |  "products": "#{#products}",
                                     |  "pricing": "#{#pricing}"
                                     |}""".stripMargin

    val result =
      parse[String](
        jsonWithComplexVariables,
        ValidationContext(
          Map(
            "products" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[String])),
            "pricing" -> Typed.genericTypeClass(
              classOf[java.util.Map[_, _]],
              List(Typed.typedClass[String], Typed.typedClass[String])
            )
          )
        )
      ).validValue.expression
        .evaluate[String](
          Context("test").withVariables(
            Map(
              "products" -> List("a", "b").asJava,
              "pricing"  -> Map("a" -> 1000, "b" -> 500).asJava,
            )
          ),
          Map.empty
        )

    result shouldBe
      """{
        |  "products": "[a, b]",
        |  "pricing": "{a=1000, b=500}"
        |}""".stripMargin
  }

  test("should return error when JSON cannot be parsed") {
    val invalidJson = """|{
                         |  "products": [
                         |}""".stripMargin

    val parsingErrors               = parse[String](invalidJson).invalidValue
    val parsingErrorsWithoutContext = parseWithoutContextValidation[String](invalidJson).invalidValue

    parsingErrors shouldBe NonEmptyList.of(JsonParsingError("expected json value got '}' (line 3, column 1)"))
    parsingErrorsWithoutContext shouldBe
      NonEmptyList.of(JsonParsingError("expected json value got '}' (line 3, column 1)"))
  }

  test("should return error when complex variable type is not in quotes") {
    val invalidJson = """|{
                         |  "products": #{#products}
                         |}""".stripMargin

    val parsingErrors = parse[String](
      invalidJson,
      ValidationContext(
        Map(
          "products" -> Typed.genericTypeClass(classOf[java.util.List[_]], List(Typed.typedClass[String])),
        )
      )
    ).invalidValue

    // This error message could be better but it requires to analyze how string ends before template variable
    parsingErrors shouldBe NonEmptyList.of(JsonParsingError("expected json value got '}' (line 3, column 1)"))
  }

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
