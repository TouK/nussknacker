package pl.touk.nussknacker.ui.process.processingtype

import cats.data.Validated.Invalid
import org.scalatest.Inside.inside
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.tests.TestFactory
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ScenarioParametersServiceTest
    extends AnyFunSuite
    with Matchers
    with ValidatedValuesDetailedMessage
    with OptionValues {

  private val oneToOneProcessingType1 = "oneToOneProcessingType1"
  private val oneToOneCategory1       = "OneToOneCategory1"
  private val oneToOneProcessingType2 = "oneToOneProcessingType2"
  private val oneToOneCategory2       = "OneToOneCategory2"

  test("processing type to category in one to one relation") {
    val combinations = Map(
      oneToOneProcessingType1 -> parametersWithCategory(oneToOneCategory1),
      oneToOneProcessingType2 -> parametersWithCategory(oneToOneCategory2),
    )
    ScenarioParametersService.create(combinations).validValue
  }

  test("unambiguous category to processing type mapping detection") {
    val categoryUsedMoreThanOnce = "CategoryUsedMoreThanOnce"
    val scenarioTypeA            = "scenarioTypeA"
    val scenarioTypeB            = "scenarioTypeB"
    val combinations = Map(
      scenarioTypeA           -> parametersWithCategory(categoryUsedMoreThanOnce),
      scenarioTypeB           -> parametersWithCategory(categoryUsedMoreThanOnce),
      oneToOneProcessingType1 -> parametersWithCategory(oneToOneCategory1),
    )

    inside(ScenarioParametersService.create(combinations)) {
      case Invalid(ParametersToProcessingTypeMappingAmbiguousException(unambiguousMapping)) =>
        unambiguousMapping should have size 1
        val (parameters, processingTypes) = unambiguousMapping.head
        parameters.category shouldEqual categoryUsedMoreThanOnce
        processingTypes should contain theSameElementsAs Set(scenarioTypeA, scenarioTypeB)
    }
  }

  test(
    "should detect situation when there is only engine setup with errors for some processing type and category combination"
  ) {
    val engineSetupName = EngineSetupName("foo")
    val combinations = Map(
      "fooProcessingType" -> ScenarioParametersWithEngineSetupErrors(
        ScenarioParameters(
          ProcessingMode.UnboundedStream,
          "fooCategory",
          engineSetupName
        ),
        List("aError")
      )
    )
    inside(ScenarioParametersService.create(combinations)) {
      case Invalid(ProcessingModeCategoryWithInvalidEngineSetupsOnly(invalidParametersCombination)) =>
        invalidParametersCombination should have size 1
        val ((processingMode, category), errors) = invalidParametersCombination.head
        processingMode shouldEqual ProcessingMode.UnboundedStream
        category shouldEqual "fooCategory"
        errors shouldEqual List("aError")
    }
  }

  test("should query processing type based on provided parameters") {
    val aCategory = "aCategory"
    val bCategory = "bCategory"

    val unboundedType = "unboundedType"
    val boundedType   = "boundedType"
    val bCategoryType = "bCategoryType"

    val service = ScenarioParametersService
      .create(
        Map(
          unboundedType ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.UnboundedStream, aCategory, EngineSetupName("aSetup")),
              List.empty
            ),
          boundedType ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.BoundedStream, aCategory, EngineSetupName("aSetup")),
              List.empty
            ),
          bCategoryType ->
            ScenarioParametersWithEngineSetupErrors(
              ScenarioParameters(ProcessingMode.UnboundedStream, bCategory, EngineSetupName("aSetup")),
              List.empty
            )
        )
      )
      .validValue

    implicit val user: LoggedUser = TestFactory.adminUser()
    service
      .queryProcessingTypeWithWritePermission(Some(aCategory), Some(ProcessingMode.UnboundedStream), None)
      .validValue shouldEqual unboundedType
    service
      .queryProcessingTypeWithWritePermission(Some(aCategory), Some(ProcessingMode.BoundedStream), None)
      .validValue shouldEqual boundedType
    service.queryProcessingTypeWithWritePermission(Some(bCategory), None, None).validValue shouldEqual bCategoryType
  }

  test("should return engine errors that are only available for user with write access to the given category") {
    val writeAccessCategory        = "writeAccessCategory"
    val noAccessCategory           = "noAccessCategory"
    val writeAccessEngineSetupName = EngineSetupName("writeAccessEngine")
    val noAccessEngineSetupName    = EngineSetupName("noAccessEngine")
    val noErrorEngineSetupName     = EngineSetupName("noErrorsEngine")
    implicit val user: LoggedUser = LoggedUser(
      id = "1",
      username = "user",
      categoryPermissions = Map(writeAccessCategory -> Set(Permission.Write))
    )

    val setupErrors = ScenarioParametersService
      .create(
        Map(
          "writeAccessProcessingType" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, writeAccessCategory, writeAccessEngineSetupName),
            List("aError")
          ),
          "writeAccessProcessingTypeWithoutError" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, writeAccessCategory, noErrorEngineSetupName),
            List.empty
          ),
          "noAccessProcessingType" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, noAccessCategory, noAccessEngineSetupName),
            List("bError")
          ),
          "noAccessProcessingTypeWithoutError" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(ProcessingMode.UnboundedStream, noAccessCategory, noErrorEngineSetupName),
            List.empty
          ),
        )
      )
      .validValue
      .engineSetupErrorsWithWritePermission

    setupErrors.get(writeAccessEngineSetupName).value shouldBe List("aError")
    setupErrors.get(noAccessEngineSetupName) shouldBe empty
  }

  test(
    "should return engine errors when user has no access to some category where engine setup was used but has access to some other category with the same engine setup"
  ) {
    val writeAccessCategory                       = "writeAccessCategory"
    val noAccessCategory                          = "noAccessCategory"
    val engineSetupNameUsedForMoreThanOneCategory = EngineSetupName("foo")
    val noErrorEngineSetupName                    = EngineSetupName("noErrorsEngine")
    implicit val user: LoggedUser = LoggedUser(
      id = "1",
      username = "user",
      categoryPermissions = Map(writeAccessCategory -> Set(Permission.Write))
    )

    val setupErrors = ScenarioParametersService
      .create(
        Map(
          "writeAccessProcessingType" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              writeAccessCategory,
              engineSetupNameUsedForMoreThanOneCategory
            ),
            List("aError")
          ),
          "writeAccessProcessingTypeNoErrors" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              writeAccessCategory,
              noErrorEngineSetupName
            ),
            List.empty
          ),
          "writeAccessProcessingType2" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              noAccessCategory,
              engineSetupNameUsedForMoreThanOneCategory
            ),
            List("aError")
          ),
          "writeAccessProcessingType2NoErrors" -> ScenarioParametersWithEngineSetupErrors(
            ScenarioParameters(
              ProcessingMode.UnboundedStream,
              noAccessCategory,
              noErrorEngineSetupName
            ),
            List.empty
          ),
        )
      )
      .validValue
      .engineSetupErrorsWithWritePermission

    setupErrors.get(engineSetupNameUsedForMoreThanOneCategory).value shouldBe List("aError")
  }

  private def parametersWithCategory(category: String, errors: List[String] = List.empty) =
    ScenarioParametersWithEngineSetupErrors(
      ScenarioParameters(ProcessingMode.UnboundedStream, category, EngineSetupName("Mock")),
      errors
    )

}
