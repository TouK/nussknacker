package pl.touk.nussknacker.restmodel.displayedgraph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.{MetaData, ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.engine.graph.exceptionhandler.ExceptionHandlerRef
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.engine.graph.NodeDataCodec._

//it would be better to have two classes but it would either to derivce from each other, which is not easy for case classes
//or we'd have to do composition which would break many things in client
// todo: id type should be ProcessName
@JsonCodec case class DisplayableProcess(id: String,
                              properties: ProcessProperties,
                              nodes: List[NodeData],
                              edges: List[Edge],
                              processingType: ProcessingType) {

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

@JsonCodec case class ValidatedDisplayableProcess(id: String,
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




@JsonCodec case class ProcessProperties(typeSpecificProperties: TypeSpecificData,
                                        exceptionHandler: ExceptionHandlerRef,
                                        isSubprocess: Boolean = false,
                                        additionalFields: Option[ProcessAdditionalFields] = None,
                                        subprocessVersions: Map[String, Long]
                            )
