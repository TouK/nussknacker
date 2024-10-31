package pl.touk.nussknacker.ui.process.processingtype

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder

import scala.collection.immutable.ListMap

class ScenarioParametersCombinationProviderTest extends AnyFunSuite with Matchers {

  test("should determine engine setup name based on deployment managers default engine setup name") {
    val givenProcessingType = "foo"
    val givenDefaultName    = EngineSetupName("Default engine name")

    val setups = ScenarioParametersDeterminer.determineEngineSetupNames(
      Map(givenProcessingType -> EngineNameInputData(givenDefaultName, (), None))
    )

    setups(givenProcessingType) shouldEqual givenDefaultName
  }

  test("should add suffix to engine setup name when find engines with different identity") {
    val engineA = EngineSetupName("engineA")
    val engineB = EngineSetupName("engineB")

    val setupIdA = "setupIdA"
    val setupIdB = "setupIdB"
    val setupIdC = "setupIdC"

    val processingTypeWithDuplicatedEngineSetup1          = s"$engineA-$setupIdA-1"
    val processingTypeWithDuplicatedEngineSetup2          = s"$engineA-$setupIdA-2"
    val processingTypeWithSameEngineNameButOtherSetup1    = s"$engineA-$setupIdB"
    val processingTypeWithSameEngineNameButOtherSetup2    = s"$engineA-$setupIdC"
    val processingTypeWithOtherEngineNameWithSameIdentity = s"$engineB-$setupIdA"

    val inputs = ListMap(
      processingTypeWithDuplicatedEngineSetup1          -> EngineNameInputData(engineA, setupIdA, None),
      processingTypeWithDuplicatedEngineSetup2          -> EngineNameInputData(engineA, setupIdA, None),
      processingTypeWithSameEngineNameButOtherSetup1    -> EngineNameInputData(engineA, setupIdB, None),
      processingTypeWithSameEngineNameButOtherSetup2    -> EngineNameInputData(engineA, setupIdC, None),
      processingTypeWithOtherEngineNameWithSameIdentity -> EngineNameInputData(engineB, setupIdA, None),
    )

    val names = ScenarioParametersDeterminer.determineEngineSetupNames(inputs)

    names(processingTypeWithDuplicatedEngineSetup1) shouldEqual engineA
    names(processingTypeWithDuplicatedEngineSetup2) shouldEqual engineA
    names(processingTypeWithSameEngineNameButOtherSetup1) shouldEqual EngineSetupName(s"$engineA 2")
    names(processingTypeWithSameEngineNameButOtherSetup2) shouldEqual EngineSetupName(s"$engineA 3")
    names(processingTypeWithOtherEngineNameWithSameIdentity) shouldEqual engineB
  }

  // TODO: add tests for processing mode collisions etc.
  test("should determine single processing mode") {
    val components = ModelDefinitionBuilder.empty
      .withUnboundedStreamSource("streaming-source")
      .build
      .components

    ScenarioParametersDeterminer.determineProcessingMode(components.components, "foo-processing-type")
  }

}
