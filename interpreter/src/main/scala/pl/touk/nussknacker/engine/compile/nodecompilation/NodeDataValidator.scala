package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.Applicative
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.TypedValue
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, SubprocessResolver}
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.NextSwitch
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser

sealed trait ValidationResponse

case class ValidationPerformed(errors: List[ProcessCompilationError],
                               parameters: Option[List[Parameter]],
                               expressionType: Option[TypingResult]) extends ValidationResponse

case object ValidationNotPerformed extends ValidationResponse

object NodeDataValidator {

  case class OutgoingEdge(target: String, edgeType: Option[EdgeType])

  def validate(nodeData: NodeData, modelData: ModelData,
               validationContext: ValidationContext,
               branchContexts: Map[String, ValidationContext],
               getFragment: String => Option[CanonicalProcess],
               outgoingEdges: List[OutgoingEdge]
              )(implicit metaData: MetaData): ValidationResponse = {
    modelData.withThisAsContextClassLoader {

      val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
        case spel: SpelExpressionParser => spel.typingDictLabels
      }
      val compiler = new NodeCompiler(modelData.processWithObjectsDefinition,
        expressionCompiler, modelData.modelClassLoader.classLoader, PreventInvocationCollector, ComponentUseCase.Validation)
      implicit val nodeId: NodeId = NodeId(nodeData.id)

      nodeData match {
        case a: Join => toValidationResponse(compiler.compileCustomNodeObject(a, Right(branchContexts), ending = false))
        case a: CustomNode => toValidationResponse(compiler.compileCustomNodeObject(a, Left(validationContext), ending = false))
        case a: Source => toValidationResponse(compiler.compileSource(a))
        case a: Sink => toValidationResponse(compiler.compileSink(a, validationContext))
        case a: Enricher => toValidationResponse(compiler.compileEnricher(a, validationContext, outputVar = Some(OutputVar.enricher(a.output))))
        case a: Processor => toValidationResponse(compiler.compileProcessor(a, validationContext))
        case a: Filter => toValidationResponse(compiler.compileExpression(a.expression, validationContext, expectedType = Typed[Boolean], outputVar = None))
        case a: Variable => toValidationResponse(compiler.compileExpression(a.value, validationContext, expectedType = typing.Unknown, outputVar = Some(OutputVar.variable(a.varName))))
        case a: VariableBuilder => toValidationResponse(compiler.compileFields(a.fields, validationContext, outputVar = Some(OutputVar.variable(a.varName))))
        case a: SubprocessOutputDefinition => toValidationResponse(compiler.compileFields(a.fields, validationContext, outputVar = Some(OutputVar.subprocess(a.outputName))))
        case a: Switch => toValidationResponse(compiler.compileSwitch(Applicative[Option].product(a.exprVal, a.expression), outgoingEdges.collect {
          case OutgoingEdge(k, Some(NextSwitch(expression))) => (k, expression)
        }, validationContext))
        case a: SubprocessInput => SubprocessResolver(getFragment).resolveInput(a).fold(
          errors => ValidationPerformed(errors.toList, None, None),
          params => toValidationResponse(compiler.compileSubprocessInput(a.copy(subprocessParams = Some(params)), validationContext))
        )
        //TODO: handle properties
        case _ => ValidationNotPerformed
      }
    }
  }

  private def toValidationResponse[T<:TypedValue](nodeCompilationResult: NodeCompilationResult[_]): ValidationResponse =
    ValidationPerformed(nodeCompilationResult.errors, nodeCompilationResult.parameters, expressionType = nodeCompilationResult.expressionType)
}

