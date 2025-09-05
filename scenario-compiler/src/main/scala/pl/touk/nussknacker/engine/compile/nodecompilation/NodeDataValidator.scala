package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{invalidNel, valid}
import cats.implicits.catsSyntaxTuple2Semigroupal
import pl.touk.nussknacker.engine.{ModelData, RuntimeMode, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError}
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
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

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
      modelData.modelDefinitionWithClasses.classDefinitions,
      modelData.modelConfig.globalParametersConfig
    ),
    expressionCompiler,
    modelData.modelClassLoader,
    Seq.empty,
    PreventInvocationCollector,
    RuntimeMode.Live,
    NodesDeploymentData.empty,
    nonServicesLazyParamStrategy = LazyParameterCreationStrategy.default
  )

  def validate(
      nodeData: NodeData,
      variableTypes: Map[String, TypingResult],
      branchVariableTypes: Option[Map[String, Map[String, TypingResult]]],
      outgoingEdges: List[OutgoingEdge],
      fragmentResolver: FragmentResolver
  )(implicit scenarioCompilationDependencies: ScenarioCompilationDependencies): ValidationResponse = {
    import scenarioCompilationDependencies._

    val validationContextWithGlobalVariablesOnly =
      GlobalVariablesPreparer(modelData.modelDefinition.expressionConfig)
        .prepareValidationContextWithGlobalVariablesOnly(jobData)

    lazy val validationContext = SingleInputNodeInputValidationContext(
      validationContextWithGlobalVariablesOnly.copy(localVariables = variableTypes)
    )
    lazy val branchCtxs = {
      val branchContexts = branchVariableTypes
        .getOrElse(Map.empty)
        .mapValuesNow { branchVariableTypes =>
          validationContextWithGlobalVariablesOnly.copy(localVariables = branchVariableTypes)
        }
      MultipleInputBranchesNodeInputValidationContext(branchContexts, validationContextWithGlobalVariablesOnly)
    }

    modelData.withModelClassloaderAsContextClassLoader {
      val compilationErrors = nodeData match {
        case a: Join =>
          toValidationResponse(
            compiler.compileCustomNodeObject(
              data = a,
              ctx = branchCtxs,
              customNodeIsEndingNode = None
            )
          )
        case a: CustomNode =>
          toValidationResponse(
            compiler.compileCustomNodeObject(
              data = a,
              ctx = validationContext,
              customNodeIsEndingNode = None
            )
          )
        case a: SourceNodeData => toValidationResponse(compiler.compileSource(a))
        case a: Sink           => toValidationResponse(compiler.compileSink(a, validationContext))
        case a: Enricher =>
          toValidationResponse(
            compiler.compileEnricher(a, validationContext)
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
          implicit val nodeId: NodeId = NodeId(a.id)
          toValidationResponse(
            compiler.compileFields(a.fields, validationContext, outputVar = Some(OutputVar.variable(a.varName)))
          )
        case a: FragmentOutputDefinition =>
          implicit val nodeId: NodeId = NodeId(a.id)
          toValidationResponse(compiler.compileFields(a.fields, validationContext, outputVar = None))
        case a: Switch =>
          toValidationResponse(
            compiler.compileSwitch(
              a,
              outgoingEdges.collect { case OutgoingEdge(k, Some(NextSwitch(expression))) =>
                (k, expression)
              },
              validationContext
            )
          )
        case a: FragmentInput =>
          validateFragment(validationContext, outgoingEdges, a, fragmentResolver)
        case Split(_, _) | FragmentUsageOutput(_, _, _, _, _) | BranchEndData(_) =>
          ValidationNotPerformed
      }

      val nodeIdErrors = IdValidator.validateNodeId(NodeId(nodeData.id)) match {
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
      inputContext: SingleInputNodeInputValidationContext,
      outgoingEdges: List[OutgoingEdge],
      a: FragmentInput,
      fragmentResolver: FragmentResolver
  )(implicit jobData: JobData) = {
    implicit val nodeId: NodeId = NodeId(a.id)
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
                  Validated.fromOption(maybeOutputName, NonEmptyList.one(UnknownFragmentOutput(output, Set(NodeId(a.id)))))
                outputName.andThen(name =>
                  inputContext.validationContext.withVariable(OutputVar.fragmentOutput(output, name), Unknown)
                )
              }
              .toList
              .sequence
            val outgoingEdgesValidated = outputs
              .map {
                case Output(name, _) if !outgoingEdges.exists(_.edgeType.contains(EdgeType.FragmentOutput(name))) =>
                  invalidNel(FragmentOutputNotDefined(name, Set(NodeId(a.id))))
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
          compiler.compileFragmentInput(a.copy(fragmentParams = Some(definition.fragmentParameters)), inputContext)
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
