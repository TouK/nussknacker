package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.Applicative
import cats.data.Validated.{invalidNel, valid}
import cats.data.{NonEmptyList, Validated}
import cats.implicits.catsSyntaxTuple2Semigroupal
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
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, FragmentResolver, Output}
import pl.touk.nussknacker.engine.definition.{FragmentComponentDefinitionExtractor, FragmentParameterValidator}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, FragmentResolver, IdValidator, Output}
import pl.touk.nussknacker.engine.definition.FragmentComponentDefinitionExtractor
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.NextSwitch
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FragmentParameterFixedValuesUserDefinedList,
  FragmentParameterNoFixedValues
}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExpressionParser
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._

sealed trait ValidationResponse

case class ValidationPerformed(
    errors: List[ProcessCompilationError],
    parameters: Option[List[Parameter]],
    expressionType: Option[TypingResult]
) extends ValidationResponse

// TODO: Remove ValidationNotPerformed
case object ValidationNotPerformed extends ValidationResponse

object NodeDataValidator {

  case class OutgoingEdge(target: String, edgeType: Option[EdgeType])

}

class NodeDataValidator(modelData: ModelData, fragmentResolver: FragmentResolver) {

  def validate(
      nodeData: NodeData,
      validationContext: ValidationContext,
      branchContexts: Map[String, ValidationContext],
      outgoingEdges: List[OutgoingEdge]
  )(implicit metaData: MetaData): ValidationResponse = {
    modelData.withThisAsContextClassLoader {

      val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withExpressionParsers {
        case spel: SpelExpressionParser => spel.typingDictLabels
      }
      val compiler = new NodeCompiler(
        modelData.modelDefinition,
        FragmentComponentDefinitionExtractor(modelData),
        expressionCompiler,
        modelData.modelClassLoader.classLoader,
        PreventInvocationCollector,
        ComponentUseCase.Validation
      )
      implicit val nodeId: NodeId = NodeId(nodeData.id)

      val compilationErrors = nodeData match {
        case a: Join => toValidationResponse(compiler.compileCustomNodeObject(a, Right(branchContexts), ending = false))
        case a: CustomNode =>
          toValidationResponse(compiler.compileCustomNodeObject(a, Left(validationContext), ending = false))
        case a: Source => toValidationResponse(compiler.compileSource(a))
        case a: Sink   => toValidationResponse(compiler.compileSink(a, validationContext))
        case a: Enricher =>
          toValidationResponse(
            compiler.compileEnricher(a, validationContext, outputVar = Some(OutputVar.enricher(a.output)))
          )
        case a: Processor => toValidationResponse(compiler.compileProcessor(a, validationContext))
        case a: Filter =>
          toValidationResponse(
            compiler.compileExpression(a.expression, validationContext, expectedType = Typed[Boolean], outputVar = None)
          )
        case a: Variable =>
          toValidationResponse(
            compiler.compileExpression(
              a.value,
              validationContext,
              expectedType = typing.Unknown,
              outputVar = Some(OutputVar.variable(a.varName))
            )
          )
        case a: VariableBuilder =>
          toValidationResponse(
            compiler.compileFields(a.fields, validationContext, outputVar = Some(OutputVar.variable(a.varName)))
          )
        case a: FragmentOutputDefinition =>
          toValidationResponse(compiler.compileFields(a.fields, validationContext, outputVar = None))
        case a: Switch =>
          toValidationResponse(
            compiler.compileSwitch(
              Applicative[Option].product(a.exprVal, a.expression),
              outgoingEdges.collect { case OutgoingEdge(k, Some(NextSwitch(expression))) =>
                (k, expression)
              },
              validationContext
            )
          )
        case a: FragmentInput           => validateFragment(validationContext, outgoingEdges, compiler, a)
        case a: FragmentInputDefinition => validateFragmentInputDefinition(compiler, validationContext, a)
        case Split(_, _) | FragmentUsageOutput(_, _, _, _) | BranchEndData(_) =>
          ValidationNotPerformed
      }

      val nodeIdErrors = IdValidator.validateNodeId(nodeData.id) match {
        case Validated.Valid(_)   => List.empty
        case Validated.Invalid(e) => e.toList
      }

      compilationErrors match {
        case e: ValidationPerformed => e.copy(errors = e.errors ++ nodeIdErrors)
        case ValidationNotPerformed => ValidationPerformed(nodeIdErrors, None, None)
      }
    }
  }

  private def validateFragment(
      validationContext: ValidationContext,
      outgoingEdges: List[OutgoingEdge],
      compiler: NodeCompiler,
      a: FragmentInput
  )(implicit nodeId: NodeId) = {
    fragmentResolver
      .resolveInput(a)
      .map { definition =>
        val outputErrors = definition.validOutputs
          .andThen { outputs =>
            val outputFieldsValidated = outputs
              .collect { case Output(name, true) => name }
              .map { output =>
                val maybeOutputName: Option[String] = a.ref.outputVariableNames.get(output)
                val outputName =
                  Validated.fromOption(maybeOutputName, NonEmptyList.one(UnknownFragmentOutput(output, Set(a.id))))
                outputName.andThen(name =>
                  validationContext.withVariable(OutputVar.fragmentOutput(output, name), Unknown)
                )
              }
              .toList
              .sequence
            val outgoingEdgesValidated = outputs
              .map {
                case Output(name, _) if !outgoingEdges.exists(_.edgeType.contains(EdgeType.FragmentOutput(name))) =>
                  invalidNel(FragmentOutputNotDefined(name, Set(a.id)))
                case _ =>
                  valid(())
              }
              .toList
              .sequence
            (outputFieldsValidated, outgoingEdgesValidated).mapN { case (_, _) => () }
          }
          .swap
          .map(_.toList)
          .valueOr(_ => List.empty)
        val parametersResponse = toValidationResponse(
          compiler.compileFragmentInput(a.copy(fragmentParams = Some(definition.fragmentParameters)), validationContext)
        )
        parametersResponse.copy(errors = parametersResponse.errors ++ outputErrors)
      }
      .valueOr(errors => ValidationPerformed(errors.toList, None, None))
  }

  private def validateFragmentInputDefinition(
      compiler: NodeCompiler,
      validationContext: ValidationContext,
      definition: FragmentInputDefinition,
  )(implicit nodeId: NodeId) = {
    val variables: Map[String, TypingResult] =
      definition.parameters.map(a => a.name -> compiler.loadFromParameter(a)).toMap
    val updatedContext = validationContext.copy(localVariables = validationContext.globalVariables ++ variables)

    val fragmentParameterErrors = definition.parameters.flatMap { param =>
      FragmentParameterValidator.validate(
        param,
        definition.id,
        compiler,
        updatedContext
      )
    }

    ValidationPerformed(
      fragmentParameterErrors,
      None,
      None
    )
  }

  private def toValidationResponse[T <: TypedValue](
      nodeCompilationResult: NodeCompilationResult[_]
  ): ValidationPerformed =
    ValidationPerformed(
      nodeCompilationResult.errors,
      nodeCompilationResult.parameters,
      expressionType = nodeCompilationResult.expressionType
    )

}
