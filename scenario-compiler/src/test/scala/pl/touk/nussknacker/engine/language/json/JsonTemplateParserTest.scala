package pl.touk.nussknacker.engine.language.json

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.Valid
import org.scalatest.EitherValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{Context, Documentation, ParamName}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError
import pl.touk.nussknacker.engine.api.generics.ExpressionParseError.{CoordinatesBasedTextRange, TextCoordinates}
import pl.touk.nussknacker.engine.api.json.encoders.ToJsonEncoder
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionTestUtils
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.expression.ExpectedType
import pl.touk.nussknacker.engine.expression.parse.TypedExpression
import pl.touk.nussknacker.engine.language.json.JsonParsingFailureToExpressionParseErrorConverter.JsonParseError
import pl.touk.nussknacker.engine.language.json.JsonTemplateParser.JsonTemplateDecodingException
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.spel.SpelFlavour
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage.convertValidatedToValuable

import java.nio.charset.{Charset, StandardCharsets}
import java.time._
import java.util
import java.util.{Currency, Locale, UUID}
import scala.jdk.CollectionConverters._

class JsonTemplateParserTest extends AnyFunSuite with Matchers with EitherValues with TableDrivenPropertyChecks {

  private val spelTemplateParser = SpelExpressionParser.default(
    getClass.getClassLoader,
    ModelDefinitionBuilder.emptyExpressionConfig,
    new SimpleDictRegistry(Map.empty),
    enableSpelForceCompile = false,
    SpelFlavour.Template,
    ClassDefinitionTestUtils.createDefinitionWithDefaultsAndExtensions,
  )

  private val spelParser = SpelExpressionParser.default(
    getClass.getClassLoader,
    ModelDefinitionBuilder.emptyExpressionConfig,
    new SimpleDictRegistry(Map.empty),
    enableSpelForceCompile = false,
    SpelFlavour.Standard,
    ClassDefinitionTestUtils.createDefinitionWithDefaultsAndExtensions,
  )

  private val sut = new JsonTemplateParser(spelTemplateParser, spelParser)

  private val validationContextWithDefaultHelpers =
    ValidationContext(globalVariables = Map("CONV" -> Typed[ToJsonConverter]))

  private val defaultHelpers = Map("CONV" -> new ToJsonConverter)

  private val validationContextWithVariables = validationContextWithDefaultHelpers.withVariablesUnsafe(
    "name"       -> Typed.typedClass[String],
    "age"        -> Typed.typedClass[Long],
    "hasConsent" -> Typed.typedClass[Boolean],
    "amount"     -> Typed.typedClass[java.math.BigDecimal],
  )

  private val evaluationContextWithVariables = Context.dummy.withVariables(
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
          s"""{
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
      parse(dataSample, expectedType = Unknown, validationContextWithVariables).map(_.returnType) shouldBe Valid(
        typingResult
      )
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

    val mapResult = parse(dataSample, expectedType = Unknown, validationContextWithVariables).validValue.expression
      .evaluate[Any](evaluationContextWithVariables, Map.empty)

    mapResult shouldBe Map(
      "name"       -> "John",
      "age"        -> 50,
      "hasConsent" -> true,
      "amount"     -> new java.math.BigDecimal("100.5"),
    ).asJava
  }

  test("should return error when JSON cannot be parsed and no expression was used") {
    val invalidJson = """{
                         |  "products": [
                         |}""".stripMargin

    val parsingErrors = parse(invalidJson, expectedType = Typed[String]).invalidValue

    parsingErrors shouldBe NonEmptyList.of(
      JsonParseError(
        "expected json value got '}'",
        Some(CoordinatesBasedTextRange(TextCoordinates(0, 2), TextCoordinates(1, 2)))
      )
    )
  }

  test("should allow to use Json type in field values where non-string value is expected") {
    val jsonWithExpressionPlaceholderInUnquotedlogicalValue =
      """{
        |  "field1": #{ #field1Value }
        |}""".stripMargin
    val validationContext = validationContextWithDefaultHelpers.withVariableUnsafe("field1Value", Typed.json)
    val typedExpression =
      parse(jsonWithExpressionPlaceholderInUnquotedlogicalValue, expectedType = Unknown, validationContext).validValue
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
          globals = defaultHelpers
        ) shouldBe Map("field1" -> field1Value).asJava
    }
  }

  test("should allow to use Json type in list elements") {
    val jsonWithExpressionPlaceholderInList =
      """[
        |  #{ #listElement }
        |]""".stripMargin
    val validationContext = validationContextWithDefaultHelpers.withVariableUnsafe("listElement", Typed.json)
    val typedExpression =
      parse(jsonWithExpressionPlaceholderInList, expectedType = Unknown, validationContext).validValue
    typedExpression.typingInfo.typingResult shouldBe Typed.genericTypeClass(
      classOf[java.util.List[_]],
      List(Typed.json)
    )

    def evaluate(listElement: Any) = {
      typedExpression.expression
        .evaluate[util.List[Any]](
          Context.dummy.withVariable("listElement", listElement),
          globals = defaultHelpers
        )
    }

    forAll(
      Table(
        ("test case", "listElement"),
        ("integer value", 1),
        ("boolean value", true),
        ("list value", List(1, 2, 3).asJava),
        ("record value", Map("field2" -> 123).asJava),
      )
    ) { (_, listElement) =>
      evaluate(listElement) shouldBe List(listElement).asJava
    }

    a[JsonTemplateDecodingException] shouldBe thrownBy {
      // strings normally are unquoted, to quote it, a user have to use #CONV.toJsonString()
      evaluate("sample string")
    }
  }

  test("should allow to use logical types") {
    val jsonTemplate =
      """{
        |  "quotedField": "#{ #logicalValue }",
        |  "quotedFieldUnknownType": "#{ #logicalValueUnknownType }",
        |  "unquotedFieldExplicitConversion": #{ #CONV.toJsonString(#logicalValue) },
        |  "unquotedFieldUnknownTypeExplicitConversion": #{ #CONV.toJsonString(#logicalValueUnknownType) }
        |}""".stripMargin

    def prepareValidationContext(logicalValue: Any) = {
      validationContextWithDefaultHelpers
        .withVariableUnsafe("logicalValue", Typed.fromInstance(logicalValue).withoutValue)
        .withVariableUnsafe("logicalValueUnknownType", Typed.json)
    }

    def prepareEvaluationContext(logicalValue: Any) = {
      Context.dummy
        .withVariable("logicalValue", logicalValue)
        .withVariable("logicalValueUnknownType", logicalValue)
    }

    forAll(
      Table(
        ("logicalValue", "expectedTypeForUnquotedField", "expectedStringValueInJson"),
        (Instant.ofEpochMilli(123L), Typed[Instant], "1970-01-01T00:00:00.123Z"),
        (OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), Typed[OffsetDateTime], "2025-01-01T00:00:00Z"),
        (ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), Typed[ZonedDateTime], "2025-01-01T00:00:00Z"),
        (LocalDateTime.of(2025, 1, 1, 0, 0, 0, 0), Typed[LocalDateTime], "2025-01-01T00:00:00"),
        (LocalDate.of(2025, 1, 1), Typed[LocalDate], "2025-01-01"),
        (LocalTime.of(12, 1), Typed[LocalTime], "12:01:00"),
        (Period.ofDays(30), Typed[Period], "P30D"),
        (Duration.ofHours(12), Typed[Duration], "PT12H"),
        (ZoneOffset.of("+01:00"), Typed[ZoneOffset], "+01:00"),
        (ZoneId.of("Europe/Warsaw"), Typed[ZoneId], "Europe/Warsaw"),
        (Locale.ENGLISH, Typed[Locale], "en"),
        (StandardCharsets.UTF_8, Typed[Charset], "UTF-8"),
        (Currency.getInstance("USD"), Typed[Currency], "USD"),
        (UUID.fromString("38a727ce-44d6-43ef-85b8-1fdde02108cf"), Typed[UUID], "38a727ce-44d6-43ef-85b8-1fdde02108cf"),
      )
    ) { (logicalValue, expectedTypeForQuotedField, expectedStringValueInJson) =>
      val validationContext = prepareValidationContext(logicalValue)
      val typedExpression   = parse(jsonTemplate, expectedType = Unknown, validationContext).validValue
      typedExpression.typingInfo.typingResult shouldBe Typed.record(
        Seq(
          "quotedField"                                -> expectedTypeForQuotedField,
          "quotedFieldUnknownType"                     -> Typed[String],
          "unquotedFieldExplicitConversion"            -> Typed.json,
          "unquotedFieldUnknownTypeExplicitConversion" -> Typed.json,
        )
      )

      val evaluationContext = prepareEvaluationContext(logicalValue)
      val evaluationResult = typedExpression.expression
        .evaluate[util.Map[String, Any]](evaluationContext, globals = defaultHelpers)
      evaluationResult shouldBe Map(
        "quotedField"                                -> logicalValue,
        "quotedFieldUnknownType"                     -> expectedStringValueInJson,
        "unquotedFieldExplicitConversion"            -> expectedStringValueInJson,
        "unquotedFieldUnknownTypeExplicitConversion" -> expectedStringValueInJson,
      ).asJava
    }

    val compiledExpressionForUnquotedField = parse(
      """{
        |  "unquotedField": #{ #logicalValue }
        |}""".stripMargin,
      expectedType = Unknown,
      prepareValidationContext(Instant.ofEpochMilli(123L))
    ).validValue
    a[JsonTemplateDecodingException] shouldBe thrownBy {
      // logical types normally are unquoted, to quote it, a user have to use #CONV.toJsonString()
      compiledExpressionForUnquotedField.expression.evaluate[Any](
        prepareEvaluationContext(Instant.ofEpochMilli(123L)),
        globals = defaultHelpers
      )
    }
  }

  test("should allow to use literal strings in places where logical types are expected") {
    forAll(
      Table(
        ("literalString", "givenType", "expectedLogicalValue"),
        ("1970-01-01T00:00:00.123Z", Typed[Instant], Instant.ofEpochMilli(123L)),
        ("2025-01-01T00:00:00Z", Typed[OffsetDateTime], OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
        ("2025-01-01T00:00:00Z", Typed[ZonedDateTime], ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)),
        ("2025-01-01T00:00:00", Typed[LocalDateTime], LocalDateTime.of(2025, 1, 1, 0, 0, 0, 0)),
        ("2025-01-01", Typed[LocalDate], LocalDate.of(2025, 1, 1)),
        ("12:01:00", Typed[LocalTime], LocalTime.of(12, 1)),
        ("P30D", Typed[Period], Period.ofDays(30)),
        ("PT12H", Typed[Duration], Duration.ofHours(12)),
        ("+01:00", Typed[ZoneOffset], ZoneOffset.of("+01:00")),
        ("Europe/Warsaw", Typed[ZoneId], ZoneId.of("Europe/Warsaw")),
        ("en", Typed[Locale], Locale.ENGLISH),
        ("UTF-8", Typed[Charset], StandardCharsets.UTF_8),
        ("USD", Typed[Currency], Currency.getInstance("USD")),
        ("38a727ce-44d6-43ef-85b8-1fdde02108cf", Typed[UUID], UUID.fromString("38a727ce-44d6-43ef-85b8-1fdde02108cf")),
      )
    ) { (literalString, givenType, expectedLogicalValue) =>
      val jsonTemplate = s""""$literalString""""

      val typedExpression = parse(jsonTemplate, expectedType = givenType, ValidationContext.empty).validValue
      typedExpression.typingInfo.typingResult shouldBe givenType

      val evaluationResult = typedExpression.expression.evaluate[Any](Context.dummy, globals = defaultHelpers)
      evaluationResult shouldBe expectedLogicalValue
    }
  }

  test("should allow to use string elements and other type elements in the list with unknown type") {
    val jsonWithExpressionPlaceholderInList =
      """[
        |  #{ #CONV.toJsonString( #listElement ) }
        |]""".stripMargin
    val validationContext = validationContextWithDefaultHelpers
      .withVariableUnsafe("listElement", Typed.json)
    val typedExpression =
      parse(jsonWithExpressionPlaceholderInList, expectedType = Unknown, validationContext).validValue
    typedExpression.typingInfo.typingResult shouldBe Typed.genericTypeClass(
      classOf[java.util.List[_]],
      List(Typed.json)
    )

    def evaluate(listElement: Any) = {
      typedExpression.expression
        .evaluate[util.List[Any]](
          Context.dummy.withVariable("listElement", listElement),
          globals = defaultHelpers
        )
    }

    forAll(
      Table(
        ("test case", "listElement"),
        ("integer value", 1),
        ("boolean value", true),
        ("string value", "sample string"),
        ("list value", List(1, 2, 3).asJava),
        ("record value", Map("field2" -> 123).asJava),
      )
    ) { (_, listElement) =>
      evaluate(listElement) shouldBe List(listElement).asJava
    }
  }

  test("should allow to use if-else templating logic in random place of json") {
    val jsonWithTemplatingLogic =
      """{
        |  "field1": 123,
        |  #{ #condition ? '"field2": 234' : '"field3": 345' }
        |}""".stripMargin

    val validationContext = validationContextWithDefaultHelpers.withVariableUnsafe("condition", Typed[Boolean])
    val typedExpression   = parse(jsonWithTemplatingLogic, expectedType = Unknown, validationContext).validValue
    typedExpression.typingInfo.typingResult shouldBe Typed.json

    def evaluate(condition: Boolean) = {
      typedExpression.expression
        .evaluate[util.Map[String, Int]](
          Context.dummy.withVariable("condition", condition),
          globals = defaultHelpers
        )
    }

    evaluate(true) shouldBe Map("field1" -> 123, "field2" -> 234).asJava
    evaluate(false) shouldBe Map("field1" -> 123, "field3" -> 345).asJava
  }

  test("should skip typing when templating logic is used") {
    val jsonWithTemplatingLogic =
      """{
        |  #{ #condition ? '"field2": 234,' : '"field3": 345,' }
        |  "field1": 123
        |}""".stripMargin

    val validationContext = validationContextWithDefaultHelpers.withVariableUnsafe("condition", Typed[Boolean])
    val typedExpression   = parse(jsonWithTemplatingLogic, expectedType = Unknown, validationContext).validValue
    typedExpression.typingInfo.typingResult shouldBe Typed.json
  }

  private def parse(
      jsonString: String,
      expectedType: TypingResult,
      ctx: ValidationContext = validationContextWithDefaultHelpers,
  ): Validated[NonEmptyList[ExpressionParseError], TypedExpression] = {
    sut.parse(jsonString, ctx, ExpectedType.loose(expectedType))
  }

  // This is a copy of ConversionUtils. We can't use it directly, because it would cause a cycle in module dependencies
  class ToJsonConverter {

    @Documentation(description = "Convert value to JSON String")
    def toJsonString(@ParamName("value") value: Any): String = {
      ToJsonEncoder.default.encodeUnsafe(value).noSpaces
    }

  }

}
