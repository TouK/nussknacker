package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.api.{MetaData, StandaloneMetaData, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.param.Parameter
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType

class NewProcessPreparer(processDefinition: Map[ProcessingType, ProcessDefinition[ObjectDefinition]]) {
  def prepareEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean): CanonicalProcess = {
    val definition = processDefinition(processingType)
    val exceptionHandlerFactory = definition.exceptionHandlerFactory
    val specificMetaData = processingType match {
      case ProcessingType.Streaming => StreamMetaData()
      case ProcessingType.RequestResponse => StandaloneMetaData(None)
    }
    val emptyCanonical = CanonicalProcess(
      metaData = MetaData(
        id = processId,
        isSubprocess = isSubprocess,
        typeSpecificData = specificMetaData
      ),
      exceptionHandlerRef = ExceptionHandlerRef(exceptionHandlerFactory.parameters.map(p => Parameter(p.name, ""))),
      nodes = List()
    )
    emptyCanonical
  }
}
