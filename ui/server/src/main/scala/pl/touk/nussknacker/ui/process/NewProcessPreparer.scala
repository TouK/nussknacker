package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.{MetaData, TypeSpecificData, UserDefinedProcessAdditionalFields}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.ProcessAdditionalFields
import pl.touk.nussknacker.ui.definition.AdditionalProcessProperty

object NewProcessPreparer {

  def apply(processTypes: Map[ProcessingType, ProcessingTypeData], additionalFields: Map[ProcessingType, Map[String, AdditionalProcessProperty]]): NewProcessPreparer =
    new NewProcessPreparer(processTypes.mapValues(_.modelData.processDefinition), processTypes.mapValues(_.emptyProcessCreate), additionalFields)

}

class NewProcessPreparer(definitions: Map[ProcessingType, ProcessDefinition[ObjectDefinition]],
                         emptyProcessCreate: Map[ProcessingType, Boolean => TypeSpecificData],
                         additionalFields: Map[ProcessingType, Map[String, AdditionalProcessProperty]]) {
  def prepareEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean): CanonicalProcess = {
    val exceptionHandlerFactory = definitions(processingType).exceptionHandlerFactory
    val specificMetaData = emptyProcessCreate(processingType)(isSubprocess)
    val emptyCanonical = CanonicalProcess(
      metaData = MetaData(
        id = processId,
        isSubprocess = isSubprocess,
        typeSpecificData = specificMetaData,
        additionalFields = defaultAdditionalFields(processingType)
      ),
      //TODO: consider better empty params - like in DefinitionResources
      exceptionHandlerRef = ExceptionHandlerRef(exceptionHandlerFactory.parameters.map(p => Parameter(p.name, Expression("spel", "")))),
      nodes = List()
    )
    emptyCanonical
  }

  private def defaultAdditionalFields(processingType: ProcessingType): Option[UserDefinedProcessAdditionalFields] = {
    Option(defaultProperties(processingType))
      .filter(_.nonEmpty)
      .map(properties => ProcessAdditionalFields(None, properties = properties))
  }

  private def defaultProperties(processingType: ProcessingType): Map[String, String] = additionalFields(processingType)
    .collect { case (name, property) if property.default.isDefined => name -> property.default.get }
}
