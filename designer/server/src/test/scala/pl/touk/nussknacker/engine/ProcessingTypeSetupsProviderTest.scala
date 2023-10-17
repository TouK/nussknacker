package pl.touk.nussknacker.engine

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.ProcessingTypeSetupsProvider.EngineSetupInputData
import pl.touk.nussknacker.engine.api.component.{SingleComponentConfig, StreamingComponent}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, ExpressionConfig, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{
  ComponentImplementationInvoker,
  ObjectDefinition,
  ObjectWithMethodDef,
  StandardObjectWithMethodDef
}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ProcessDefinition, toExpressionDefinition}
import pl.touk.nussknacker.engine.processingtypesetup.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.collection.immutable.ListMap

class ProcessingTypeSetupsProviderTest extends AnyFunSuite with Matchers {

  test("should determine engine setup name based on deployment managers default engine setup name") {
    val givenProcessingType = "foo"
    val givenDefaultName    = EngineSetupName("Default engine name")

    val setups = ProcessingTypeSetupsProvider.determineEngineSetups(
      Map(givenProcessingType -> EngineSetupInputData(givenDefaultName, (), List.empty, None))
    )

    setups(givenProcessingType).name shouldEqual givenDefaultName
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
      processingTypeWithDuplicatedEngineSetup1          -> EngineSetupInputData(engineA, setupIdA, List.empty, None),
      processingTypeWithDuplicatedEngineSetup2          -> EngineSetupInputData(engineA, setupIdA, List.empty, None),
      processingTypeWithSameEngineNameButOtherSetup1    -> EngineSetupInputData(engineA, setupIdB, List.empty, None),
      processingTypeWithSameEngineNameButOtherSetup2    -> EngineSetupInputData(engineA, setupIdC, List.empty, None),
      processingTypeWithOtherEngineNameWithSameIdentity -> EngineSetupInputData(engineB, setupIdA, List.empty, None),
    )

    val setups = ProcessingTypeSetupsProvider.determineEngineSetups(inputs)

    setups(processingTypeWithDuplicatedEngineSetup1).name shouldEqual engineA
    setups(processingTypeWithDuplicatedEngineSetup2).name shouldEqual engineA
    setups(processingTypeWithSameEngineNameButOtherSetup1).name shouldEqual EngineSetupName(s"$engineA 2")
    setups(processingTypeWithSameEngineNameButOtherSetup2).name shouldEqual EngineSetupName(s"$engineA 3")
    setups(processingTypeWithOtherEngineNameWithSameIdentity).name shouldEqual engineB
  }

  test("should determine single processing mode") {
    val sources = Map(
      "streaming-source" -> WithCategories.anyCategory(new SourceFactory with StreamingComponent)
    )
    val modelDefinition: ProcessDefinition[ObjectWithMethodDef] = createModelDefinition(sources)
    ProcessingTypeSetupsProvider.determineProcessingMode(
      modelDefinition,
      ProcessingTypeCategory("streaming-source", "Default")
    )
  }

  // TODO: add tests for other combinations of processing modes

  private def createModelDefinition(sources: Map[String, WithCategories[SourceFactory]]) = {
    val sourcesDefinitions = sources.mapValuesNow { sourceWithCategory =>
      val objectDefinition =
        ObjectDefinition(List.empty, None, sourceWithCategory.categories, SingleComponentConfig.zero)
      StandardObjectWithMethodDef(
        ComponentImplementationInvoker.nullImplementationInvoker,
        sourceWithCategory.value,
        objectDefinition,
        classOf[Any]
      )
    }
    val modelDefinition = ProcessDefinition[ObjectWithMethodDef](
      sourcesDefinitions,
      Map.empty,
      Map.empty,
      Map.empty,
      toExpressionDefinition(ExpressionConfig.empty),
      ClassExtractionSettings.Default
    )
    modelDefinition
  }

}
