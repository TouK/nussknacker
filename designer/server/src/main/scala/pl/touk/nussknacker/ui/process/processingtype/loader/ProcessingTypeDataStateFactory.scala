package pl.touk.nussknacker.ui.process.processingtype.loader

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine._
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.process.processingtype._
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataState
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

import scala.util.Success

object ProcessingTypeDataStateFactory extends LazyLogging {

  def create(
      modelDataWithInputByProcessingType: Map[ProcessingType, ValueWithRestriction[
        ModelDataWithProcessingTypeDataInput
      ]],
      deploymentDataByProcessingType: Map[ProcessingType, DeploymentData],
  ): Validated[
    ScenarioParametersConfigurationError,
    ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]
  ] = {
    val processingTypesData = modelDataWithInputByProcessingType
      .map { case (processingType, modelDataWithInputWithRestriction) =>
        processingType -> modelDataWithInputWithRestriction.map { modelDataWithInput =>
          val deploymentData = deploymentDataByProcessingType(processingType)
          val processingTypeData = ProcessingTypeData.createProcessingTypeData(
            processingType,
            modelDataWithInput.modelData,
            deploymentData,
            modelDataWithInput.category,
            modelDataWithInput.componentDefinitionExtractionMode,
          )
          processingTypeData
        }
      }

    // Here all processing types are loaded and we are ready to perform additional configuration validations
    // to assert the loaded configuration is correct (fail-fast approach).
    // Using NussknackerInternalUser is a hack, we should split deployment data from model data
    val processingTypesDataWithoutRestriction =
      processingTypesData.mapValuesNow(_.valueWithAllowedAccess(Permission.Read)(NussknackerInternalUser.instance).get)
    CombinedProcessingTypeData.create(processingTypesDataWithoutRestriction).map { combinedData =>
      new ProcessingTypeDataState(
        processingTypesData,
        // We return Success instead of passing here Try result of CombinedProcessingTypeData.create because
        // we want to break reloading logic instead of creating a new state with failing combined date
        // Try in ProcessingTypeDataState.combinedDataTry is only for initial state purpose when this data are not initialized yet
        Success(combinedData)
      )
    }
  }

  final case class ModelDataWithProcessingTypeDataInput(
      modelData: ModelData,
      category: String,
      componentDefinitionExtractionMode: ComponentDefinitionExtractionMode
  ) extends AutoCloseable {

    override def close(): Unit = {
      modelData.close()
    }

  }

  def toValueWithRestriction(processingTypeData: ProcessingTypeData): ValueWithRestriction[ProcessingTypeData] = {
    ValueWithRestriction.userWithAccessRightsToAnyOfCategories(processingTypeData, Set(processingTypeData.category))
  }

}
