package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.Applicative
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{invalidNel, valid}
import cats.implicits.catsSyntaxTuple2Semigroupal
import pl.touk.nussknacker.engine.{ComponentUseCase, ModelData}
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{FragmentOutputNotDefined, UnknownFragmentOutput}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, FragmentResolver, IdValidator, Output}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.expression.parse.TypedValue
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.NextSwitch
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.resultcollector.PreventInvocationCollector
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

class NodeDataValidator(modelData: ModelData) {

  private val expressionCompiler = ExpressionCompiler.withoutOptimization(modelData).withLabelsDictTyper

  private val compiler = new NodeCompiler(
    modelData.modelDefinition,
    new FragmentParametersDefinitionExtractor(
      modelData.modelClassLoader,
      modelData.modelDefinitionWithClasses.classDefinitions
    ),
    expressionCompiler,
    modelData.modelClassLoader,
    Seq.empty,
    PreventInvocationCollector,
    ComponentUseCase.Validation,
    NodesDeploymentData.empty,
    nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
  )

  def validate(
      nodeData: NodeData,
      validationContext: ValidationContext,
      branchContexts: Map[String, ValidationContext],
      outgoingEdges: List[OutgoingEdge],
      fragmentResolver: FragmentResolver
  )(implicit jobData: JobData): ValidationResponse = {
    modelData.withThisAsContextClassLoader {
      implicit val nodeId: NodeId = NodeId(nodeData.id)

      val compilationErrors = nodeData match {
        case a: Join => toValidationResponse(compiler.compileCustomNodeObject(a, Right(branchContexts), ending = false))
        case a: CustomNode =>
          toValidationResponse(compiler.compileCustomNodeObject(a, Left(validationContext), ending = false))
        case a: SourceNodeData => toValidationResponse(compiler.compileSource(a))
        case a: Sink           => toValidationResponse(compiler.compileSink(a, validationContext))
        case a: Enricher =>
          toValidationResponse(
            compiler.compileEnricher(a, validationContext, outputVar = OutputVar.enricher(a.output))
          )
        case a: Processor => toValidationResponse(compiler.compileProcessor(a, validationContext))
        case a: Filter =>
          toValidationResponse(
            compiler.compileFilter(a, validationContext)
          )
        case a: Variable =>
          toValidationResponse(
            compiler.compileVariable(a, validationContext)
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
        case a: FragmentInput => validateFragment(validationContext, outgoingEdges, a, fragmentResolver)
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
      a: FragmentInput,
      fragmentResolver: FragmentResolver
  )(implicit nodeId: NodeId, jobData: JobData) = {
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

  private def toValidationResponse[T <: TypedValue](
      nodeCompilationResult: NodeCompilationResult[_]
  ): ValidationPerformed =
    ValidationPerformed(
      nodeCompilationResult.errors,
      nodeCompilationResult.parameters,
      expressionType = nodeCompilationResult.expressionType
    )

}
