package pl.touk.nussknacker.ui.validation

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessAdditionalFields
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.api.helpers.{ProcessTestData, TestFactory}
import pl.touk.nussknacker.ui.definition.{AdditionalProcessProperty, PropertyType}

class AdditionalPropertiesValidatorTest extends FunSuite with Matchers {
  private val validator = new AdditionalPropertiesValidator(
    TestFactory.mapProcessingTypeDataProvider(
      "streaming" -> Map(
        "propReq" -> AdditionalProcessProperty(
          "foo", PropertyType.integer, None, isRequired = true, None
        ),
        "propOpt" -> AdditionalProcessProperty(
          "foo", PropertyType.string, None, isRequired = false, None
        )
      )),
    "testError"
  )

  test("validate non empty config with required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> "5"
      ))
    ))
    val result = validator.validate(process)

    checkOne(result)

  }

  test("validate non empty config without required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propOpt" -> "a"
      ))
    ))
    val result = validator.validate(process)

    checkOne(result, Seq("empty", "propReq"))
  }

  test("validate non empty config with empty required property") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propReq" -> ""
      ))
    ))
    val result = validator.validate(process)

    checkOne(result, Seq("empty", "propReq"), Seq("type", "propReq"))
  }

  test("validate non empty config with required property of invalid type") {
    val process = ProcessTestData.displayableWithAdditionalFields(Some(
      ProcessAdditionalFields(None, Set.empty, properties = Map(
        "propOpt" -> "a"
      ))
    ))
    val result = validator.validate(process)

    checkOne(result, Seq("empty", "propReq"))
  }

  test("validate empty config") {
    val process = ProcessTestData.displayableWithAdditionalFields(None)
    val result = validator.validate(process)

    checkOne(result, Seq("type", "propReq"))
  }

  private def checkOne(result: ValidationResult, keywords: Seq[String]*): Unit = {
    result.isOk shouldBe keywords.isEmpty

    val errors = result.errors.processPropertiesErrors
    errors.size shouldBe keywords.length

    keywords.foreach { keywordSeq =>
      errors.exists { err =>
        keywordSeq.forall(err.message contains _)
      }
    }
  }
}
