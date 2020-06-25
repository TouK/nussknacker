package pl.touk.nussknacker.engine.compile.nodevalidation

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.transformation.SingleInputGenericNodeTransformation
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo}
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Filter, NodeData, Sink, Source, WithParameters}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

/*
  Currently we only validate filter nodes. In the future we should implement validation/compilation for all node types
  and refactor compiler accordingly, to avoid duplication
 */
trait NodeDataValidator[T<:NodeData] {

  def validate(nodeData: T, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse

}

sealed trait ValidationResponse

case class ValidationPerformed(errors: List[ProcessCompilationError], parameters: Option[List[Parameter]]) extends ValidationResponse

case object ValidationNotPerformed extends ValidationResponse

object NodeDataValidator {

  def validate(nodeData: NodeData, modelData: ModelData, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = {
    nodeData match {
      case a:Filter => new FilterValidator(modelData).validate(a, validationContext)
      case a:CustomNode => new CustomNodeValidator(modelData).validate(a, validationContext)
      case a:Source => new SourceNodeValidator(modelData).validate(a, validationContext)
      case a:Sink => new SinkNodeValidator(modelData).validate(a, validationContext)
      case a => EmptyValidator.validate(a, validationContext)
    }
  }

}


class FilterValidator(modelData: ModelData) extends NodeDataValidator[Filter] {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.dictServices.dictRegistry,
      modelData.processDefinition.expressionConfig,
      modelData.processDefinition.settings
    )

  override def validate(nodeData: Filter, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = {
    val validation: ValidatedNel[ProcessCompilationError, _] =
      expressionCompiler.compile(nodeData.expression, Some(NodeTypingInfo.DefaultExpressionId), validationContext, Typed[Boolean])(NodeId(nodeData.id))
    ValidationPerformed(validation.fold(_.toList, _ => Nil), None)
  }
}

trait WithParametersNodeValidator[T <: NodeData with WithParameters] extends NodeDataValidator[T] {

  protected def modelData: ModelData

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(
    modelData.modelClassLoader.classLoader,
    modelData.dictServices.dictRegistry,
    modelData.processDefinition.expressionConfig,
    modelData.processDefinition.settings
  )

  private val expressionEvaluator
  = ExpressionEvaluator.unOptimizedEvaluator(GlobalVariablesPreparer(modelData.processWithObjectsDefinition.expressionConfig))

  private val nodeValidator = new GenericNodeTransformationValidator(expressionCompiler, expressionEvaluator)

  protected def validateGenericTransformer(obj: Any,
                                           nodeData: NodeData with WithParameters,
                                           validationContext: ValidationContext,
                                           outputVar: Option[String], default: Any => ValidationResponse)(implicit metaData: MetaData): ValidationResponse = {
    obj match {
      case transform:SingleInputGenericNodeTransformation[_] =>
        implicit val nodeId: NodeId = NodeId(nodeData.id)
        nodeValidator.validateNode(transform, nodeData.parameters, validationContext, outputVar) match {
          case Valid(result) =>
            ValidationPerformed(result.errors, Some(result.parameters))
          case Invalid(e) => ValidationPerformed(e.toList, None)
        }
      case other => default(other)
    }
  }

}

class CustomNodeValidator(val modelData: ModelData) extends WithParametersNodeValidator[CustomNode] {

  override def validate(nodeData: CustomNode, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = {
    val transformer = modelData.processWithObjectsDefinition.customStreamTransformers(nodeData.nodeType)._1.obj
    //TODO: handle standard case (non-generic transformer)
    validateGenericTransformer(transformer, nodeData, validationContext, nodeData.outputVar, _ => ValidationNotPerformed)
  }
}

class SinkNodeValidator(val modelData: ModelData) extends WithParametersNodeValidator[Sink] {

  override def validate(nodeData: Sink, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = {
    val transformer = modelData.processWithObjectsDefinition.sinkFactories(nodeData.ref.typ)._1.obj
    //TODO: handle standard case (non-generic transformer)
    validateGenericTransformer(transformer, nodeData, validationContext, None, _ => ValidationNotPerformed)
  }
}

class SourceNodeValidator(val modelData: ModelData) extends WithParametersNodeValidator[Source] {

  override def validate(nodeData: Source, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = {
    val transformer = modelData.processWithObjectsDefinition.sourceFactories(nodeData.ref.typ).obj
    //TODO: handle standard case (non-generic transformer)
    validateGenericTransformer(transformer, nodeData, validationContext, None, _ => ValidationNotPerformed)
  }
}

object EmptyValidator extends NodeDataValidator[NodeData] {
  override def validate(nodeData: NodeData, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = ValidationNotPerformed
}

                                                                                                           