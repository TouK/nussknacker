package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.{MetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

object NewProcessPreparer {

  def apply(processTypes: Map[ProcessingType, ProcessingTypeData]): NewProcessPreparer =
    new NewProcessPreparer(processTypes.mapValues(_.modelData.processDefinition), processTypes.mapValues(_.emptyProcessCreate))

}

class NewProcessPreparer(definitions: Map[ProcessingType, ProcessDefinition[ObjectDefinition]],
                         emptyProcessCreate: Map[ProcessingType, Boolean => TypeSpecificData]) {
  def prepareEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean): CanonicalProcess = {
    val exceptionHandlerFactory = definitions(processingType).exceptionHandlerFactory
    val specificMetaData = emptyProcessCreate(processingType)(isSubprocess)
    val emptyCanonical = CanonicalProcess(
      metaData = MetaData(
        id = processId,
        isSubprocess = isSubprocess,
        typeSpecificData = specificMetaData
      ),
      //TODO: consider better empty params - like in DefinitionResources
      exceptionHandlerRef = ExceptionHandlerRef(exceptionHandlerFactory.parameters.map(p => Parameter(p.name, Expression("spel", "")))),
      nodes = List()
    )
    emptyCanonical
  }
}
