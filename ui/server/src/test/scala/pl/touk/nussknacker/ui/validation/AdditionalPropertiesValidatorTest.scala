package pl.touk.nussknacker.ui.validation

import cats.data.Validated.Invalid
import cats.data.ValidatedNel
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingRequiredProperty, UnknownProperty}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

class AdditionalPropertiesValidatorTest extends AnyFunSuite with Matchers {
  private val reqFieldName = "propReq"
  private val regexpFieldName = "propRegExp"
  private val optionalFieldName = "propOpt"
  private val optFixedFieldName = "propOptFixed"
  private val possibleValues = List(FixedExpressionValue("a", "a"), FixedExpressionValue("b", "b"))
  private val label = "foo"

  private val validator = new AdditionalPropertiesValidator(Map(
        reqFieldName -> AdditionalPropertyConfig(
          None,
          None,
          Some(List(LiteralParameterValidator.integerValidator, MandatoryParameterValidator)),
          Some(label)),
        regexpFieldName -> AdditionalPropertyConfig(
          None,
          None,
          Some(List(LiteralParameterValidator.numberValidator)),
          Some(label)),
        optionalFieldName -> AdditionalPropertyConfig(
          None,
          Some(StringParameterEditor),
          None,
          Some(label)),
        optFixedFieldName -> AdditionalPropertyConfig(
          None,
          Some(FixedValuesParameterEditor(possibleValues)),
          Some(List(FixedValuesValidator(possibleValues))),
          Some(label)
        )
      )
  )

  test("validate non empty config with required property") {
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        "propReq" -> "5"
      ))
    ))
    val result = validator.validate(process)
    result shouldBe 'valid
  }

  test("validate non empty config without required property") {
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        "propOpt" -> "a"
      ))
    ))

    val result = validator.validate(process)

    assertError(result, MissingRequiredProperty("propReq", Some("foo")))
  }

  test("validate non empty config with empty required property") {
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        "propReq" -> ""
      ))
    ))

    val result = validator.validate(process)

    //assertError(result, EmptyMandatoryParameter("propOpt", None))

  }

  test("validate regexp config with empty property") {
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        "propReq" -> "1",
        "propRegExp" -> ""
      ))
    ))

    val result = validator.validate(process)
    result shouldBe 'invalid
  }

  test("validate config with invalid property") {
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        "propReq" -> "1",
        "propRegExp" -> "asd"
      ))
    ))

    val result = validator.validate(process)

    assertError(result, MissingRequiredProperty("propOpt", None))
  }

  test("validate non empty config with required property with wrong type") {
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        "propReq" -> "some text"
      ))
    ))

    val result = validator.validate(process)

    assertError(result, MissingRequiredProperty("propOpt", None))

  }

  test("validate empty config") {
    val process = prepareScenario(None)

    val result = validator.validate(process)

    assertError(result, MissingRequiredProperty("propReq", Some("foo")))

  }

  test("validate non empty config with fixed value property with wrong value") {
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        optFixedFieldName -> "some text"
      ))
    ))

    val result = validator.validate(process)

    assertError(result, MissingRequiredProperty("propOpt", None))

  }

  test("validate non empty config with unknown property") {
    val unknownProperty = "unknown"
    val process = prepareScenario(Some(
      ProcessAdditionalFields(None, properties = Map(
        "propReq" -> "5",
        "unknown" -> "some text"
      ))
    ))

    val result = validator.validate(process)

    assertError(result, UnknownProperty("unknown"))

  }


  private def assertError(result: ValidatedNel[ProcessCompilationError, Unit], compilationError: ProcessCompilationError*) = {
    result.leftMap(_.toList) should matchPattern {
      case Invalid(errors) if errors == compilationError.toList =>
    }
  }

  private def prepareScenario(additional: Option[ProcessAdditionalFields]): CanonicalProcess = {
    CanonicalProcess(MetaData("test", StreamMetaData(), additional), Nil, Nil)
  }
}
