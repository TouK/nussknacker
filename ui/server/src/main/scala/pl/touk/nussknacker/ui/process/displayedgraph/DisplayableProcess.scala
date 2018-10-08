package pl.touk.nussknacker.ui.process.displayedgraph

import pl.touk.nussknacker.engine.api.{MetaData, TypeSpecificData, UserDefinedProcessAdditionalFields}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.ui.validation.ProcessValidation
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.displayablenode._
import pl.touk.nussknacker.ui.validation.ValidationResults.ValidationResult

//it would be better to have two classes but it would either to derivce from each other, which is not easy for case classes
//or we'd have to do composition which would break many things in client
// todo: id type should be ProcessName
case class DisplayableProcess(id: String,
                              properties: ProcessProperties,
                              nodes: List[NodeData],
                              edges: List[Edge],
                              processingType: ProcessingType) {

  def validated(validation: ProcessValidation) =
    new ValidatedDisplayableProcess(this, validation.validate(this))
  
  def withSuccessValidation(): ValidatedDisplayableProcess = {
    new ValidatedDisplayableProcess(this, ValidationResult.success)
  }

  val metaData = MetaData(
    id = id,
    typeSpecificData = properties.typeSpecificProperties,
    isSubprocess = properties.isSubprocess,
    additionalFields = properties.additionalFields,
    subprocessVersions = properties.subprocessVersions
  )

}

case class ValidatedDisplayableProcess(id: String,
                                       properties: ProcessProperties,
                                       nodes: List[NodeData],
                                       edges: List[Edge],
                                       processingType: ProcessingType,
                                       validationResult: ValidationResult) {

  def this(displayableProcess: DisplayableProcess, validationResult: ValidationResult) =
    this(
      displayableProcess.id,
      displayableProcess.properties,
      displayableProcess.nodes,
      displayableProcess.edges,
      displayableProcess.processingType,
      validationResult
    )

  def toDisplayable = DisplayableProcess(id, properties, nodes, edges, processingType)

}




case class ProcessProperties(typeSpecificProperties: TypeSpecificData,
                             exceptionHandler: ExceptionHandlerRef,
                             isSubprocess: Boolean = false,
                             additionalFields: Option[UserDefinedProcessAdditionalFields] = None,
                             subprocessVersions: Map[String, Long]
                            )
