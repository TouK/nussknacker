package pl.touk.nussknacker.ui.process.processingtype

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioParameters, ScenarioParametersWithEngineSetupErrors}
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage
import pl.touk.nussknacker.ui.api.helpers.TestFactory
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ScenarioParametersServiceTest extends AnyFunSuite with Matchers with ValidatedValuesDetailedMessage {

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

  test("ambiguous category to processing type mapping") {
    val categoryUsedMoreThanOnce = "CategoryUsedMoreThanOnce"
    val scenarioTypeA            = "scenarioTypeA"
    val scenarioTypeB            = "scenarioTypeB"
    val combinations = Map(
      scenarioTypeA           -> parametersWithCategory(categoryUsedMoreThanOnce),
      scenarioTypeB           -> parametersWithCategory(categoryUsedMoreThanOnce),
      oneToOneProcessingType1 -> parametersWithCategory(oneToOneCategory1),
    )

    val unambiguousMapping = ScenarioParametersService.create(combinations).invalidValue.unambiguousMapping
    unambiguousMapping should have size 1
    val (parameters, processingTypes) = unambiguousMapping.head
    parameters.category shouldEqual categoryUsedMoreThanOnce
    processingTypes should contain theSameElementsAs Set(scenarioTypeA, scenarioTypeB)
  }

  private def parametersWithCategory(category: String) =
    ScenarioParametersWithEngineSetupErrors(
      ScenarioParameters(ProcessingMode.UnboundedStream, category, EngineSetupName("Flink")),
      List.empty
    )

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
      .getProcessingTypeWithWritePermission(Some(aCategory), Some(ProcessingMode.UnboundedStream), None)
      .validValue shouldEqual unboundedType
    service
      .getProcessingTypeWithWritePermission(Some(aCategory), Some(ProcessingMode.BoundedStream), None)
      .validValue shouldEqual boundedType
    service.getProcessingTypeWithWritePermission(Some(bCategory), None, None).validValue shouldEqual bCategoryType
  }

}
