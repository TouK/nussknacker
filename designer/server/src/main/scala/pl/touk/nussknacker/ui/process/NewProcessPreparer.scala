package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.{ProcessingTypeData, TypeSpecificInitialData}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

object NewProcessPreparer {

  def apply(processTypes: ProcessingTypeDataProvider[ProcessingTypeData, _], additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _]): NewProcessPreparer =
    new NewProcessPreparer(processTypes.mapValues(_.typeSpecificInitialData), additionalFields)

}


class NewProcessPreparer(emptyProcessCreate: ProcessingTypeDataProvider[TypeSpecificInitialData, _],
                         additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _]) {
  def prepareEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean): CanonicalProcess = {
    val creator = emptyProcessCreate.forTypeUnsafe(processingType)
    val specificMetaData = if(isSubprocess) creator.forFragment _ else creator.forScenario _
    val typeSpecificData = specificMetaData(ProcessName(processId), processingType)
    val emptyCanonical = CanonicalProcess(
      metaData = MetaData(
        id = processId,
        additionalFields = defaultAdditionalFields(processingType, typeSpecificData)
      ),
      nodes = List.empty,
      additionalBranches = List.empty
    )
    emptyCanonical
  }

  // TODO: new typespecific defaults based on config - important for setting generated slug
  // TODO: remove initialTypeSpeciifcData if this works correctly
  private def defaultAdditionalFields(processingType: ProcessingType, typeSpecificData: TypeSpecificData): ProcessAdditionalFields = {
    ProcessAdditionalFields(None, properties = defaultProperties(processingType, typeSpecificData), typeSpecificData.metaDataType)
  }

  private def defaultProperties(processingType: ProcessingType,
                                typeSpecificData: TypeSpecificData): Map[String, String] = {
    // TODO: check if this is correct handling of fragments
    if (typeSpecificData.isSubprocess) {
      return typeSpecificData.toMap
    }

    val additionalFieldsConfig = additionalFields.forTypeUnsafe(processingType)

    validateRequiredConfigPresent(typeSpecificData, additionalFieldsConfig)

    additionalFieldsConfig.collect {
      case (name, parameterConfig) if !typeSpecificData.toMap.contains(name) =>
        name -> parameterConfig.defaultValue.getOrElse("")
      case (name, parameterConfig) if typeSpecificData.toMap.contains(name) =>
        val typeSpecificDefault = typeSpecificData.toMap(name)
        val parameterDefault = parameterConfig.defaultValue
        // In case when the parameter default is None, we get the default from type specific initial data. This is necessary
        // when defaults are dynamic - for example slug in request-response
        if (parameterDefault.isDefined && parameterDefault.get != typeSpecificDefault) {
          throw new IllegalStateException(
            s"""Property with name: $name has inconsistent default configuration. For properties that are also present
               | in TypeSpecificData the defaults have to match. The default value for property was: ${parameterDefault.get}
               | and for corresponding property in TypeSpecificData was ${typeSpecificDefault}""".stripMargin)
        }
        name -> typeSpecificDefault
    }
  }

  private def validateRequiredConfigPresent(typeSpecificData: TypeSpecificData,
                                            propertyConfig: Map[String, AdditionalPropertyConfig]): Unit = {
    typeSpecificData.toMap.collect {
      case (key, _) if !propertyConfig.contains(key) =>
        throw new IllegalStateException(
          s"""Configuration for property with name: $key is not present. Type specific properties have to have explicit
             | configuration (AdditionalPropertyConfig)""".stripMargin
        )
    }
  }
}
