package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.AllowedProcessingModesExtractor
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.processingtypesetup.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}
import pl.touk.nussknacker.ui.process.ProcessCategoryService

import scala.collection.immutable.ListMap

object ProcessingTypeSetupsProvider {

  def processingTypeSetups(
      processingTypesData: Map[ProcessingType, ProcessingTypeData],
      categoryService: ProcessCategoryService
  ): Map[ProcessingTypeCategory, ProcessingTypeSetup] = {
    val engineSetupsInputData = processingTypesData.mapValuesNow(data => EngineSetupInputData(data.deploymentData))
    val engineSetups          = determineEngineSetups(engineSetupsInputData)
    for {
      (processingType, data) <- processingTypesData
      category               <- categoryService.getProcessingTypeCategories(processingType)
    } yield {
      val processingTypeCategory = ProcessingTypeCategory(processingType, category)
      val processingMode         = determineProcessingMode(data.modelData.modelDefinition, processingTypeCategory)
      val engineSetup            = engineSetups(processingType)
      processingTypeCategory -> ProcessingTypeSetup(processingMode, engineSetup)
    }
    // FIXME: Add verification that processing type can be ambiguity determined based on ProcessingTypeParametersCombination
    //        + tests for that + remove ConfigProcessCategoryService.checkCategoryToProcessingTypeMappingAmbiguity
  }

  private[engine] def determineProcessingMode(
      modelDefinition: ProcessDefinition[ObjectWithMethodDef],
      processingTypeCategory: ProcessingTypeCategory
  ): ProcessingMode = {
    val componentsProcessingModes =
      AllowedProcessingModesExtractor.componentsProcessingModes(modelDefinition, processingTypeCategory.category)
    componentsProcessingModes.values.toList match {
      case Nil =>
        // FIXME: some proper errors and tests for that
        throw new IllegalStateException(
          s"Empty list of components for processing type category pair: $processingTypeCategory"
        )
      case nonEmptyList =>
        val intersection = nonEmptyList.foldLeft(ProcessingMode.all) {
          case (acc, Some(componentProcessingModes)) => acc.intersect(componentProcessingModes)
          case (acc, None)                           => acc
        }
        intersection.toList match {
          case oneMode :: Nil => oneMode
          // FIXME: some proper errors and tests for that
          case Nil =>
            throw new IllegalStateException(
              s"Detected collision of allowed processing modes for processing type category pair: $processingTypeCategory among components: $componentsProcessingModes"
            )
          case moreThenOne =>
            throw new IllegalStateException(
              s"More than one allowed processing modes: $moreThenOne for processing type category pair: $processingTypeCategory for components: $componentsProcessingModes"
            )
        }
    }
  }

  private[engine] def determineEngineSetups(
      deploymentData: Map[ProcessingType, EngineSetupInputData]
  ): Map[ProcessingType, EngineSetup] = {
    val grouped = deploymentData.toList.map { case (processingType, in) =>
      (in.defaultName, in.identity) -> (processingType, in.errors)
    }.toGroupedMap

    grouped
      .foldLeft((ListMap.empty[ProcessingType, EngineSetup], Map.empty[EngineSetupName, Int])) {
        case ((accResult, accIndexForName), ((groupedDefaultName, _), processingTypesErrors)) =>
          val pickedName      = groupedDefaultName
          val index           = accIndexForName.getOrElse(pickedName, 1)
          val engineSetupName = if (index == 1) pickedName else pickedName.withSuffix(s" $index")
          val newEntries = processingTypesErrors.map { case (processingType, errors) =>
            processingType -> EngineSetup(engineSetupName, errors)
          }
          (accResult ++ newEntries, accIndexForName + (pickedName -> (index + 1)))
      }
      ._1
  }

  private[engine] final case class EngineSetupInputData(
      defaultName: EngineSetupName,
      identity: Any,
      errors: List[String],
      specifiedName: Option[EngineSetupName]
  )

  private[engine] object EngineSetupInputData {

    def apply(deploymentData: DeploymentData): EngineSetupInputData = {
      val errors = deploymentData.validDeploymentManager.swap.map(_.toList).valueOr(_ => Nil)
      // FIXME: add optional engineSetupName to deployment configuration
      EngineSetupInputData(deploymentData.defaultEngineSetupName, deploymentData.engineSetupIdentity, errors, None)
    }

  }

}
