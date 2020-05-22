package pl.touk.nussknacker.engine.compile.nodevalidation

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo}
import pl.touk.nussknacker.engine.graph.node.{Filter, NodeData}

/*
  Currently we only validate filter nodes. In the future we should implement validation/compilation for all node types
  and refactor compiler accordingly, to avoid duplication
 */
trait NodeDataValidator[T<:NodeData] {

  def compile(nodeData: T, validationContext: ValidationContext)(implicit metaData: MetaData): List[ProcessCompilationError]

}

object NodeDataValidator {

  def validate(nodeData: NodeData, modelData: ModelData, validationContext: ValidationContext)(implicit metaData: MetaData): List[ProcessCompilationError] = {
    nodeData match {
      case a:Filter => new FilterValidator(modelData).compile(a, validationContext)
      case a => EmptyValidator.compile(a, validationContext)
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

  override def compile(nodeData: Filter, validationContext: ValidationContext)(implicit metaData: MetaData): List[ProcessCompilationError] = {
    val validation: ValidatedNel[ProcessCompilationError, _] =
      expressionCompiler.compile(nodeData.expression, Some(NodeTypingInfo.DefaultExpressionId), validationContext, Typed[Boolean])(NodeId(nodeData.id))
    validation.fold(_.toList, _ => Nil)
  }
}

object EmptyValidator extends NodeDataValidator[NodeData] {
  override def compile(nodeData: NodeData, validationContext: ValidationContext)(implicit metaData: MetaData): List[ProcessCompilationError] = Nil
}

