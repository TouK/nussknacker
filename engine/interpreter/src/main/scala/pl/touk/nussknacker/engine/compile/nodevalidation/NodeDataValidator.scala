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
import pl.touk.nussknacker.engine.graph.node.{CustomNode, Filter, NodeData}

object NodeDataValidator {

  def validate(nodeData: NodeData, modelData: ModelData, validationContext: ValidationContext)(implicit metaData: MetaData): (Option[List[Parameter]], List[ProcessCompilationError]) = {
    nodeData match {
      case a:Filter => new FilterValidator(modelData).compile(a, validationContext)
      case a:CustomNode => new CustomNodeValidator(modelData).compile(a, validationContext)
      case a => DummyValidator.compile(a, validationContext)
    }
  }

}

trait NodeDataValidator[T<:NodeData] {

  def compile(nodeData: T, validationContext: ValidationContext)(implicit metaData: MetaData): (Option[List[Parameter]], List[ProcessCompilationError])

}

class FilterValidator(modelData: ModelData) extends NodeDataValidator[Filter] {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(
      modelData.modelClassLoader.classLoader,
      modelData.dictServices.dictRegistry,
      modelData.processDefinition.expressionConfig,
      modelData.processDefinition.settings
    )

  override def compile(nodeData: Filter, validationContext: ValidationContext)(implicit metaData: MetaData): (Option[List[Parameter]], List[ProcessCompilationError]) = {
    val validation: ValidatedNel[ProcessCompilationError, _] = expressionCompiler.compile(nodeData.expression, Some(NodeTypingInfo.DefaultExpressionId), validationContext, Typed[Boolean])(NodeId(nodeData.id))
    (Some(List(Parameter[Boolean]("expression"))), validation.fold(_.toList, _ => Nil))
  }
}

class CustomNodeValidator(modelData: ModelData) extends NodeDataValidator[CustomNode] {

  private val nodeValidator = new NodeTransformerValidator(modelData)

  override def compile(nodeData: CustomNode, validationContext: ValidationContext)(implicit metaData: MetaData): (Option[List[Parameter]], List[ProcessCompilationError]) = {
    val transformer = modelData.processWithObjectsDefinition.customStreamTransformers(nodeData.nodeType)._1.obj
    transformer match {
      case a:SingleInputGenericNodeTransformation[_] =>
        implicit val nodeId: NodeId = NodeId(nodeData.id)
        nodeValidator.validateNode(a, nodeData.parameters, validationContext) match {
          case Valid(result) =>
            (Some(result.parameters), result.errors)
          case Invalid(e) => (None, e.toList)
        }
      case _ =>(None, Nil)
    }
  }
}

object DummyValidator extends NodeDataValidator[NodeData] {
  override def compile(nodeData: NodeData, validationContext: ValidationContext)(implicit metaData: MetaData): (Option[List[Parameter]], List[ProcessCompilationError]) = (None, Nil)
}

