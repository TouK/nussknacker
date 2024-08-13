package pl.touk.nussknacker.engine.util.service

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedSingleParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent,
  WithStaticParameters
}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{
  NodeDependency,
  OutputVariableNameDependency,
  Parameter,
  TypedNodeDependency
}
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName

import scala.concurrent.{ExecutionContext, Future}
import scala.runtime.BoxedUnit

/*
  This is helper trait for creating Service which has parameter definitions fixed in designer (i.e. no parameters depending on each other)
  but parameter definitions which are not fixed/known at compile time. Good example are services which take parameter
  list from external source (e.g. configuration, OpenAPI definition, database).
  For dynamic parameters use SingleInputDynamicComponent, for parameters known at compile time - use @MethodToInvoke
 */
trait EagerServiceWithStaticParameters
    extends EagerService
    with SingleInputDynamicComponent[ServiceInvoker]
    with WithStaticParameters {

  override type State = TypingResult

  private val metaData = TypedNodeDependency[MetaData]

  override def staticParameters: List[Parameter] = parameters

  def parameters: List[Parameter]

  def hasOutput: Boolean

  def createServiceInvoker(
      eagerParameters: Map[ParameterName, Any],
      lazyParameters: Map[ParameterName, LazyParameter[AnyRef]],
      typingResult: TypingResult,
      metaData: MetaData
  ): ServiceInvoker

  def returnType(
      validationContext: ValidationContext,
      parameters: Map[ParameterName, DefinedSingleParameter]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, TypingResult]

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(Nil, _) if parameters.nonEmpty => NextParameters(parameters)
    case TransformationStep(list, _) if hasOutput =>
      val output = returnType(context, list.toMap)
      FinalResults.forValidation(context, output.swap.map(_.toList).getOrElse(Nil))(
        _.withVariable(OutputVariableNameDependency.extract(dependencies), output.getOrElse(Unknown), None)
      )
    case TransformationStep(_, _) => FinalResults(context, Nil)
  }

  override def nodeDependencies: List[NodeDependency] =
    if (hasOutput) List(OutputVariableNameDependency, metaData) else List(metaData)

  override final def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[TypingResult]
  ): ServiceInvoker =
    createServiceInvoker(
      params.nameToValueMap.filterNot { case (_, param) => param.isInstanceOf[LazyParameter[_]] },
      params.nameToValueMap.collect { case (name, param: LazyParameter[AnyRef]) => (name, param) },
      finalState.getOrElse(Unknown),
      metaData.extract(dependencies)
    )

}

/*
  Like in EagerServiceWithStaticParameters, but for simpler case, when return type is also known in designer (i.e. it does not depend on parameters)
 */
trait EagerServiceWithStaticParametersAndReturnType extends EagerServiceWithStaticParameters {

  def returnType: TypingResult

  // TODO: This method should be removed - instead, developers should deliver it's own ServiceInvoker to avoid
  //       mixing implementation logic with definition logic. Before that we should fix EagerService Lifecycle handling.
  //       See notice next to EagerService
  def invoke(eagerParameters: Map[ParameterName, Any])(
      implicit ec: ExecutionContext,
      collector: InvocationCollectors.ServiceInvocationCollector,
      contextId: ContextId,
      metaData: MetaData,
      componentUseCase: ComponentUseCase
  ): Future[Any]

  override final def createServiceInvoker(
      eagerParameters: Map[ParameterName, Any],
      lazyParameters: Map[ParameterName, LazyParameter[AnyRef]],
      typingResult: TypingResult,
      metaData: MetaData
  ): ServiceInvoker = new ServiceInvokerImplementation(eagerParameters, lazyParameters, metaData)

  override def hasOutput: Boolean = !List(Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(returnType)

  override def returnType(
      validationContext: ValidationContext,
      parameters: Map[ParameterName, DefinedSingleParameter]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, TypingResult] = Valid(returnType)

  private class ServiceInvokerImplementation(
      eagerParameters: Map[ParameterName, Any],
      lazyParameters: Map[ParameterName, LazyParameter[AnyRef]],
      metaData: MetaData
  ) extends ServiceInvoker {

    override def invoke(context: Context)(
        implicit ec: ExecutionContext,
        collector: InvocationCollectors.ServiceInvocationCollector,
        componentUseCase: ComponentUseCase
    ): Future[Any] = {
      implicit val contextId: ContextId   = ContextId(context.id)
      implicit val metaImplicit: MetaData = metaData
      val evaluatedLazyParameters         = lazyParameters.map { case (name, value) => (name, value.evaluate(context)) }
      EagerServiceWithStaticParametersAndReturnType.this.invoke(eagerParameters ++ evaluatedLazyParameters)
    }

  }

}
