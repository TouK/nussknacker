package pl.touk.nussknacker.ui.process.processingtype

import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList

import scala.collection.immutable.ListMap

object ScenarioParametersDeterminer {

  def determineProcessingMode(
      components: List[ComponentDefinitionWithImplementation],
      processingType: ProcessingType,
  ): ProcessingMode = {
    val componentsToProcessingMode = components.map { component =>
      val allowedProcessingModes = component.implementation.allowedProcessingModes
      component.id -> allowedProcessingModes
    }.toMap

    componentsToProcessingMode.values.toList match {
      // FIXME: some proper errors and tests for that
      case Nil => throw new IllegalStateException(s"Empty list of components for processing type: $processingType")
      case nonEmptyList =>
        val intersection = nonEmptyList.foldLeft(ProcessingMode.all) {
          case (acc, Some(componentProcessingModes)) => acc.intersect(componentProcessingModes)
          case (acc, None)                           => acc
        }
        intersection.toList match {
          case oneMode :: Nil => oneMode
          // FIXME: some proper errors and tests for that
          case Nil =>
            val componentsWithDefinedAllowedProcessingModes = componentsToProcessingMode.collect {
              case (id, Some(modes)) => id -> modes
            }
            throw new IllegalStateException(
              s"Detected collision of allowed processing modes for processing type: $processingType among components: $componentsWithDefinedAllowedProcessingModes"
            )
          case moreThanOne =>
            throw new IllegalStateException(
              s"More than one allowed processing modes: $moreThanOne for processing type: $processingType for components: $componentsToProcessingMode"
            )
        }
    }
  }

  def determineEngineSetupNames(
      nameInputDatas: Map[ProcessingType, EngineNameInputData]
  ): Map[ProcessingType, EngineSetupName] = {
    val grouped = nameInputDatas.toList.map { case (processingType, in) =>
      (in.nameSpecifiedInConfig.getOrElse(in.defaultName), in.identity) -> processingType
    }.toGroupedMap

    grouped
      .foldLeft((ListMap.empty[ProcessingType, EngineSetupName], Map.empty[EngineSetupName, Int])) {
        case ((accResult, accIndexForName), ((groupedDefaultName, _), processingTypesErrors)) =>
          val pickedName      = groupedDefaultName
          val index           = accIndexForName.getOrElse(pickedName, 1)
          val engineSetupName = if (index == 1) pickedName else pickedName.withSuffix(s" $index")
          val newEntries = processingTypesErrors.map { processingType =>
            processingType -> engineSetupName
          }
          (accResult ++ newEntries, accIndexForName + (pickedName -> (index + 1)))
      }
      ._1
  }

}

final case class EngineNameInputData(
    defaultName: EngineSetupName,
    identity: Any,
    nameSpecifiedInConfig: Option[EngineSetupName]
)
