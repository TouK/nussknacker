package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.{ProcessingTypeData, TypeSpecificInitialData}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.process.ProcessingType
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

object NewProcessPreparer {

  def apply(processTypes: ProcessingTypeDataProvider[ProcessingTypeData], additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]]): NewProcessPreparer =
    new NewProcessPreparer(processTypes.mapValues(_.modelData.processDefinition), processTypes.mapValues(_.typeSpecificInitialData), additionalFields)

}

class NewProcessPreparer(definitions: ProcessingTypeDataProvider[ProcessDefinition[ObjectDefinition]],
                         emptyProcessCreate: ProcessingTypeDataProvider[TypeSpecificInitialData],
                         additionalFields: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]]) {
  def prepareEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean): CanonicalProcess = {
    val exceptionHandlerFactory = definitions.forTypeUnsafe(processingType).exceptionHandlerFactory
    val creator = emptyProcessCreate.forTypeUnsafe(processingType)
    val specificMetaData = if(isSubprocess) creator.forFragment else creator.forScenario
    val emptyCanonical = CanonicalProcess(
      metaData = MetaData(
        id = processId,
        typeSpecificData = specificMetaData,
        additionalFields = defaultAdditionalFields(processingType)
      ),
      //TODO: consider better empty params - like in DefinitionResources
      exceptionHandlerRef = ExceptionHandlerRef(exceptionHandlerFactory.parameters.map(p => Parameter(p.name, Expression("spel", "")))),
      nodes = List.empty,
      additionalBranches = List.empty
    )
    emptyCanonical
  }

  private def defaultAdditionalFields(processingType: ProcessingType): Option[ProcessAdditionalFields] = {
    Option(defaultProperties(processingType))
      .filter(_.nonEmpty)
      .map(properties => ProcessAdditionalFields(None, properties = properties))
  }

  private def defaultProperties(processingType: ProcessingType): Map[String, String] = additionalFields.forTypeUnsafe(processingType)
    .collect { case (name, parameterConfig) if parameterConfig.defaultValue.isDefined => name -> parameterConfig.defaultValue.get }
}
