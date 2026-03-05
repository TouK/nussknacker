package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.{JobData, NodeId, NodeName, VariableConstants}
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{FatalUnknownError, MissingParameters}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.ReturningType
import pl.touk.nussknacker.engine.api.typed.typing.{TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.NodeValidationExceptionHandler
import pl.touk.nussknacker.engine.compile.nodecompilation.StaticComponentOutputValidationContextDeterminer.conntextAfterService
import pl.touk.nussknacker.engine.definition.component.methodbased.MethodBasedComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.graph.node.{CustomNodeData, FragmentInputDefinition, NodeData, WithOptionalOutputVar}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import shapeless.syntax.typeable.typeableOps

class StaticComponentOutputValidationContextDeterminer(
    globalVariablesPreparer: GlobalVariablesPreparer
) extends LazyLogging {

  type ComponentExecutor = Any

  def contextAfterNode(
      nodeData: NodeData,
      customNodeIsEndingNode: Option[Boolean],
      staticComponent: MethodBasedComponentDefinitionWithImplementation,
      validComponentExecutor: ValidatedNel[ProcessCompilationError, ComponentExecutor],
      inputContext: NodeInputValidationContext,
  )(
      implicit jobData: JobData
  ): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    implicit val implicitComponentId: ComponentId = staticComponent.id
    implicit val nodeId: NodeId                   = nodeData.id
    implicit val nodeName: NodeName               = nodeData.name
    def outputContextBasedOnResultType(
        returnTypeOpt: Option[TypingResult]
    ): ValidatedNel[ProcessCompilationError, ValidationContext] =
      (staticComponent.componentType, nodeData, inputContext) match {
        case (ComponentType.Source, _, _) =>
          contextAfterSource(returnTypeOpt)
        case (ComponentType.Fragment, _: FragmentInputDefinition, _) =>
          Valid(contextAfterFragmentInputDefinition(staticComponent.parameters))
        case (ComponentType.Sink, _, singleInputContext: SingleInputNodeInputValidationContext) =>
          Valid(singleInputContext.validationContext)
        case (
              ComponentType.Service,
              serviceData: NodeData with WithOptionalOutputVar,
              singleInputContext: SingleInputNodeInputValidationContext,
            ) =>
          conntextAfterService(
            singleInputContext,
            serviceData.outputVar.map(OutputVar.enricher),
            returnTypeOpt
          )
        case (ComponentType.CustomComponent, customNodeData: CustomNodeData, _) =>
          contextAfterCustomComponent(
            customNodeData,
            customNodeIsEndingNode,
            inputContext,
            returnTypeOpt
          )
        case _ =>
          throw new IllegalStateException(
            s"Illegal combination of component type [${staticComponent.componentType}], node [$nodeData] and input context [$inputContext]"
          )
      }

    validComponentExecutor.fold(
      _ => outputContextBasedOnResultType(staticComponent.returnType),
      executor =>
        contextAfterMethodBasedCreatedComponentExecutor(
          executor,
          inputContext,
          (executor: ComponentExecutor) =>
            outputContextBasedOnResultType(
              returnTypeFromExecutor(executor) orElse staticComponent.returnType
            )
        )
    )
  }

  private def returnTypeFromExecutor(componentExecutor: ComponentExecutor): Option[TypingResult] =
    componentExecutor.cast[ReturningType].map(_.returnType)

  private def contextAfterMethodBasedCreatedComponentExecutor[ComponentExecutor](
      executor: ComponentExecutor,
      validationContexts: NodeInputValidationContext,
      handleNonContextTransformingExecutor: ComponentExecutor => ValidatedNel[
        ProcessCompilationError,
        ValidationContext
      ]
  )(
      implicit nodeId: NodeId,
      nodeName: NodeName,
      jobData: JobData
  ): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    NodeValidationExceptionHandler.handleExceptionsInValidation {
      val contextTransformationDefOpt = executor.cast[AbstractContextTransformation].map(_.definition)
      (contextTransformationDefOpt, validationContexts) match {
        case (
              Some(transformation: ContextTransformationDef),
              SingleInputNodeInputValidationContext(validationContext)
            ) =>
          // copying global variables because custom transformation may override them -> TODO: in ValidationContext
          transformation.transform(validationContext).map(_.copy(globalVariables = validationContext.globalVariables))
        case (
              Some(transformation: JoinContextTransformationDef),
              MultipleInputBranchesNodeInputValidationContext(branchEndContexts, _)
            ) =>
          // copying global variables because custom transformation may override them -> TODO: in ValidationContext
          transformation
            .transform(branchEndContexts)
            .map(_.copy(globalVariables = contextWithOnlyGlobalVariables.globalVariables))
        case (Some(transformation), ctx) =>
          Invalid(
            FatalUnknownError(
              message = s"Invalid ContextTransformation class $transformation for contexts: $ctx",
              nodeId = Some(nodeId),
              nodeName = Some(nodeName)
            )
          ).toValidatedNel
        case (None, _) =>
          handleNonContextTransformingExecutor(executor)
      }
    }(nodeId, nodeName, jobData.metaData)
  }

  private def contextAfterSource(returnTypeOpt: Option[TypingResult])(
      implicit nodeId: NodeId,
      jobData: JobData
  ): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    contextWithOnlyGlobalVariables.withVariable(
      VariableConstants.InputVariableName,
      returnTypeOpt.getOrElse(Unknown),
      paramName = None
    )
  }

  private def contextAfterCustomComponent(
      data: CustomNodeData,
      customNodeIsEndingNode: Option[Boolean],
      branchCtx: NodeInputValidationContext,
      returnTypeOpt: Option[TypingResult]
  )(
      implicit nodeId: NodeId,
      jobData: JobData
  ): ValidatedNel[ProcessCompilationError, ValidationContext] = {
    val validationContext = branchCtx match {
      case SingleInputNodeInputValidationContext(validationContext) => validationContext
      case MultipleInputBranchesNodeInputValidationContext(_, validationContextWithGlobalVariablesOnly) =>
        validationContextWithGlobalVariablesOnly
    }

    def ctxWithVar(outputVar: OutputVar, typ: TypingResult) = validationContext
      .withVariable(outputVar, typ)
      // ble... NonEmptyList is invariant...
      .asInstanceOf[ValidatedNel[ProcessCompilationError, ValidationContext]]

    (data.outputVar, returnTypeOpt, customNodeIsEndingNode) match {
      case (Some(varName), Some(typ), _) => ctxWithVar(OutputVar.customNode(varName), typ)
      case (None, None, _)               => Valid(validationContext)
      case (Some(outputVarValue), None, _) =>
        logger.warn(
          s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. " +
            s"Found outputVar = $outputVarValue but custom node [${data.id}] used by the node doesn't need it. It will be skipped."
        )
        Valid(validationContext)
      case (None, Some(_), Some(false)) =>
        Invalid(NonEmptyList.of(MissingParameters(Set(ParameterName("OutputVariable")))))
      case (None, Some(_), _) => Valid(validationContext)
    }
  }

  private[nodecompilation] def contextAfterFragmentInputDefinition(
      parameterDefinitions: List[Parameter]
  )(implicit jobData: JobData): ValidationContext = {
    val variables: Map[String, TypingResult] = parameterDefinitions.map(a => a.name.value -> a.typ).toMap
    contextWithOnlyGlobalVariables.copy(localVariables = variables)
  }

  private def contextWithOnlyGlobalVariables(
      implicit jobData: JobData
  ): ValidationContext =
    globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData)

}

object StaticComponentOutputValidationContextDeterminer extends LazyLogging {

  private[nodecompilation] def conntextAfterService(
      inputContext: SingleInputNodeInputValidationContext,
      outputVarName: Option[OutputVar],
      returnTypeOpt: Option[TypingResult]
  )(
      implicit nodeId: NodeId,
      componentId: ComponentId,
      jobData: JobData
  ): ValidatedNel[ProcessCompilationError, ValidationContext] =
    outputVarName match {
      case Some(out) =>
        returnTypeOpt
          .map(inputContext.validationContext.withVariable(out, _))
          .getOrElse {
            logger.warn(
              s"Scenario [${jobData.metaData.name}] node [$nodeId] compilation warning. " +
                s"Found ${out.fieldName} = ${out.outputName} but service [${componentId.name}] used by the node doesn't need it. It will be skipped."
            )
            Valid(inputContext.validationContext)
          }
      case None => Valid(inputContext.validationContext)
    }

}
