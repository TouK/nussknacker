package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{invalidNel, valid}
import cats.implicits.catsSyntaxTuple2Semigroupal
import pl.touk.nussknacker.engine.{ModelData, RuntimeMode, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.{JobData, NodeId}
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.context.{OutputVar, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{FragmentOutputNotDefined, UnknownFragmentOutput}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, FragmentResolver, NameValidator, Output}
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeDataValidator.OutgoingEdge
import pl.touk.nussknacker.engine.definition.fragment.FragmentParametersDefinitionExtractor
import pl.touk.nussknacker.engine.expression.parse.TypedValue
import pl.touk.nussknacker.engine.graph.EdgeType
import pl.touk.nussknacker.engine.graph.EdgeType.NextSwitch
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.resultcollector.{PreventInvocationCollector, ProductionServiceInvocationCollector}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer

sealed trait ValidationResponse

case class ValidationPerformed(
    errors: List[ProcessCompilationError],
    parameters: Option[List[Parameter]],
    expressionType: Option[TypingResult],
    outputValidationContext: Option[ValidationContext],
) extends ValidationResponse

// TODO: Remove ValidationNotPerformed
case object ValidationNotPerformed extends ValidationResponse

object NodeDataValidator {

  case class OutgoingEdge(target: String, edgeType: Option[EdgeType])

}

class NodeDataValidator(modelData: ModelData) {

  private val compiler = NodeCompiler.forValidation(modelData)

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

    modelData.withModelClassloaderAsContextClassLoader {
      val compilationErrors = nodeData match {
        case a: CompilableNodeData =>
          toValidationResponse(
            compiler.compileNode[CompilableNodeData](a, variableTypes, branchVariableTypes, outgoingEdges)
          )
        case a: FragmentInput =>
          validateFragment(validationContext, outgoingEdges, a, fragmentResolver)
        case Split(_, _, _) | FragmentUsageOutput(_, _, _, _, _, _) | BranchEndData(_) =>
          ValidationNotPerformed
      }

      val nodeIdErrors = NameValidator.validateNodeName(nodeData) match {
        case Validated.Valid(_)   => List.empty
        case Validated.Invalid(e) => e.toList
      }

      compilationErrors match {
        case e: ValidationPerformed => e.copy(errors = e.errors ++ nodeIdErrors)
        case ValidationNotPerformed =>
          ValidationPerformed(nodeIdErrors, parameters = None, expressionType = None, outputValidationContext = None)
      }
    }
  }

  private def validateFragment(
      inputContext: SingleInputNodeInputValidationContext,
      outgoingEdges: List[OutgoingEdge],
      a: FragmentInput,
      fragmentResolver: FragmentResolver
  )(implicit jobData: JobData) = {
    implicit val nodeId: NodeId = a.id
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
                  Validated.fromOption(
                    maybeOutputName,
                    NonEmptyList.one(UnknownFragmentOutput(output, Set(a.id)))
                  )
                outputName.andThen(name =>
                  inputContext.validationContext.withVariable(OutputVar.fragmentOutput(output, name), Unknown)
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
          compiler.compileFragmentInput(a.copy(fragmentParams = Some(definition.fragmentParameters)), inputContext)
        )
        parametersResponse.copy(errors = parametersResponse.errors ++ outputErrors)
      }
      .valueOr(errors => ValidationPerformed(errors.toList, None, None, None))
  }

  private def toValidationResponse[T <: TypedValue](
      nodeCompilationResult: NodeCompilationResult[_]
  ): ValidationPerformed =
    ValidationPerformed(
      nodeCompilationResult.errors,
      nodeCompilationResult.parameters,
      expressionType = nodeCompilationResult.expressionType,
      outputValidationContext = nodeCompilationResult.validationContext.toOption
    )

}
