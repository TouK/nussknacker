package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.{TypedExpression, TypedValue}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.spel.SpelExpressionParser

/*
  Currently we only validate filter nodes. In the future we should implement validation/compilation for all node types
  and refactor compiler accordingly, to avoid duplication
 */
trait NodeDataValidator[T <: NodeData] {

  def validate(nodeData: T, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse

}

sealed trait ValidationResponse

case class ValidationPerformed(errors: List[ProcessCompilationError],
                              parameters: Option[List[Parameter]],
                              typedExpression: Option[TypedExpression]) extends ValidationResponse

case object ValidationNotPerformed extends ValidationResponse



object NodeDataValidator {

  def validate(nodeData: NodeData, modelData: ModelData,
               validationContext: ValidationContext,
               branchContexts: Map[String, ValidationContext]
              )(implicit metaData: MetaData): ValidationResponse = {
    modelData.withThisAsContextClassLoader {

      val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
        case spel: SpelExpressionParser => spel.typingDictLabels
      }
      val compiler = new NodeCompiler(modelData.processWithObjectsDefinition,
        expressionCompiler, modelData.modelClassLoader.classLoader)
      implicit val nodeId: NodeId = NodeId(nodeData.id)

      nodeData match {
        case a: Join => toValidationResponse(compiler.compileCustomNodeObject(a, Right(branchContexts), ending = false))
        case a: CustomNode => toValidationResponse(compiler.compileCustomNodeObject(a, Left(validationContext), ending = false))
        case a: Source => toValidationResponse(compiler.compileSource(a))
        case a: Sink => toValidationResponse(compiler.compileSink(a, validationContext))
        case a: Enricher => toValidationResponse(compiler.compileEnricher(a, validationContext))
        case a: Processor => toValidationResponse(compiler.compileProcessor(a, validationContext))

        case a: Filter => new FilterValidator(expressionCompiler).validate(a, validationContext)
        case a: Variable => toValidationResponse(compiler.compileExpression(a.value, validationContext))
        //TODO: handle variable builder, switch, subprocess
        //subprocess is tricky as we have to handle resolution :/
        case a => EmptyValidator.validate(a, validationContext)
      }
    }
  }
    private def toValidationResponse[T<:TypedValue](nodeCompilationResult: NodeCompilationResult[_]): ValidationResponse =
      ValidationPerformed(nodeCompilationResult.errors, nodeCompilationResult.parameters, typedExpression = nodeCompilationResult.typedExpression)
}

//TODO: this should be converted somehow towards NodeCompiler, so that validation logic is the same during node validation and whole process compilation
class FilterValidator(expressionCompiler: ExpressionCompiler) extends NodeDataValidator[Filter] {

  override def validate(nodeData: Filter, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = {
    val validation: ValidatedNel[ProcessCompilationError, _] =
      expressionCompiler.compile(nodeData.expression, Some(NodeTypingInfo.DefaultExpressionId), validationContext, Typed[Boolean])(NodeId(nodeData.id))
    ValidationPerformed(validation.fold(_.toList, _ => Nil), parameters = None, typedExpression = None)
  }
}

object EmptyValidator extends NodeDataValidator[NodeData] {
  override def validate(nodeData: NodeData, validationContext: ValidationContext)(implicit metaData: MetaData): ValidationResponse = ValidationNotPerformed
}

                                                                                                           