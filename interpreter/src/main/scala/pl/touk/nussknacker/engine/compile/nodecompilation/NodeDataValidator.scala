package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.Applicative
import cats.data.Validated.valid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{FragmentOutputNotDefined, UnknownFragmentOutput}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.expression.TypedValue
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{MetaData, NodeId}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, InputValidationResponse, Output, SubprocessResolver}
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.NextSwitch
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._

sealed trait ValidationResponse

case class ValidationPerformed(errors: List[ProcessCompilationError],
                               parameters: Option[List[Parameter]],
                               expressionType: Option[TypingResult]) extends ValidationResponse

case object ValidationNotPerformed extends ValidationResponse

object NodeDataValidator {

  case class OutgoingEdge(target: String, edgeType: Option[EdgeType])

}

class NodeDataValidator(modelData: ModelData, subprocessResolver: SubprocessResolver) {

  def validate(nodeData: NodeData,
               validationContext: ValidationContext,
               branchContexts: Map[String, ValidationContext],
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
        case a: SubprocessOutputDefinition => toValidationResponse(compiler.compileFields(a.fields, validationContext, outputVar = None))
        case a: Switch => toValidationResponse(compiler.compileSwitch(Applicative[Option].product(a.exprVal, a.expression), outgoingEdges.collect {
          case OutgoingEdge(k, Some(NextSwitch(expression))) => (k, expression)
        }, validationContext))
        case a: SubprocessInput => validateSubprocess(validationContext, outgoingEdges, compiler, a)
        case _ => ValidationNotPerformed
      }
    }
  }

  private def validateSubprocess(validationContext: ValidationContext,
                                 outgoingEdges: List[OutgoingEdge],
                                 compiler: NodeCompiler,
                                 a: SubprocessInput)
                                (implicit nodeId: NodeId) = {
    subprocessResolver.resolveInput(a).map {
      case InputValidationResponse(params, outputs) =>
        val outputFieldsValidationErrors = outputs.collect { case Output(name, true) => name }.map { output =>
          val maybeOutputName: Option[String] = a.ref.outputVariableNames.get(output)
          val outputName = Validated.fromOption(maybeOutputName, NonEmptyList.one(UnknownFragmentOutput(output, Set(a.id))))
          outputName.andThen(name => validationContext.withVariable(OutputVar.fragmentOutput(output, name), Unknown))
        }.toList.sequence.swap.toList.flatMap(_.toList)
        val outgoingEdgesErrors = outputs.collect {
          case Output(name, _) if !outgoingEdges.exists(_.edgeType.contains(EdgeType.SubprocessOutput(name))) =>
            FragmentOutputNotDefined(name, Set(a.id))
        }
        def getSubprocessParamDefinition(paramName: String): ValidatedNel[ProcessCompilationError, Parameter] = {
          valid(params.getOrElse(
            paramName,
            // It shouldn't happen because on this stage we have parameters already validated by SubprocessResolver
            throw new IllegalStateException(s"Missing parameter definition: $paramName for node: $a")))
        }
        val parametersResponse = toValidationResponse(compiler.compileSubprocessInput(a, getSubprocessParamDefinition, validationContext))
        parametersResponse.copy(errors = parametersResponse.errors ++ outputFieldsValidationErrors ++ outgoingEdgesErrors)
    }.valueOr(errors => ValidationPerformed(errors.toList, None, None))
  }


  private def toValidationResponse[T<:TypedValue](nodeCompilationResult: NodeCompilationResult[_]): ValidationPerformed =
    ValidationPerformed(nodeCompilationResult.errors, nodeCompilationResult.parameters, expressionType = nodeCompilationResult.expressionType)
}

