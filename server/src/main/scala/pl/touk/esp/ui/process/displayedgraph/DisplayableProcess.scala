package pl.touk.esp.ui.process.displayedgraph

import pl.touk.esp.engine.api.{MetaData, TypeSpecificData, UserDefinedProcessAdditionalFields}
import pl.touk.esp.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.esp.engine.graph.node.NodeData
import pl.touk.esp.ui.validation.ProcessValidation
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.displayablenode._
import pl.touk.esp.ui.validation.ValidationResults.ValidationResult

//it would be better to have two classes but it would either to derivce from each other, which is not easy for case classes
//or we'd have to do composition which would break many things in client
case class DisplayableProcess(id: String,
                              properties: ProcessProperties,
                              nodes: List[NodeData],
                              edges: List[Edge],
                              processingType: ProcessingType,
                              validationResult: Option[ValidationResult] = None) {
  def validated(validation: ProcessValidation) =
    copy(validationResult = Some(validation.validate(this)))

  val metaData = MetaData(id, properties.typeSpecificProperties, properties.additionalFields)

}

case class ProcessProperties(typeSpecificProperties: TypeSpecificData,
                             exceptionHandler: ExceptionHandlerRef,
                             additionalFields: Option[UserDefinedProcessAdditionalFields])
