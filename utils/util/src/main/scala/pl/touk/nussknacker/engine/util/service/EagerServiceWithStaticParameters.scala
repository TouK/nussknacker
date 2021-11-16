package pl.touk.nussknacker.engine.util.service

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.context.transformation.{DefinedSingleParameter, NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, Parameter, TypedNodeDependency}
import pl.touk.nussknacker.engine.api.process.RunMode
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{ContextId, EagerService, LazyParameter, MetaData, ServiceInvoker}

import scala.concurrent.{ExecutionContext, Future}
import scala.runtime.BoxedUnit

/*
  This is helper trait for creating Service which has parameter definitions fixed in designer (i.e. no parameters depending on each other)
  but parameter definitions which are not fixed/known at compile time. Good example are services which take parameter
  list from external source (e.g. configuration, OpenAPI definition, database).
  For dynamic parameters use SingleInputGenericNodeTransformation, for parameters known at compile time - use @MethodToInvoke
 */
trait EagerServiceWithStaticParameters extends EagerService with SingleInputGenericNodeTransformation[ServiceInvoker] {

  override type State = TypingResult

  private val metaData = TypedNodeDependency(classOf[MetaData])

  def parameters: List[Parameter]

  def hasOutput: Boolean

  def serviceImplementation(eagerParameters: Map[String, Any],
                            typingResult: TypingResult,
                            metaData: MetaData): ServiceInvoker

  def returnType(validationContext: ValidationContext, parameters: Map[String, DefinedSingleParameter]): ValidatedNel[ProcessCompilationError, TypingResult]

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])
                                    (implicit nodeId: ProcessCompilationError.NodeId): NodeTransformationDefinition = {
    case TransformationStep(Nil, _) if parameters.nonEmpty => NextParameters(parameters)
    case TransformationStep(list, _) if hasOutput =>
      val output = returnType(context, list.toMap)
      FinalResults.forValidation(context, output.swap.map(_.toList).getOrElse(Nil))(
        _.withVariable(OutputVariableNameDependency.extract(dependencies), output.getOrElse(Unknown), None))
    case TransformationStep(_, _) => FinalResults(context, Nil)
  }

  override def nodeDependencies: List[NodeDependency] = if (hasOutput) List(OutputVariableNameDependency, metaData) else List(metaData)

  override def implementation(params: Map[String, Any], dependencies: List[NodeDependencyValue], finalState: Option[TypingResult]): ServiceInvoker = {
    serviceImplementation(
      params.filterNot(_._2.isInstanceOf[LazyParameter[_]]),
      finalState.getOrElse(Unknown),
      metaData.extract(dependencies))
  }

}

/*
  Like in EagerServiceWithStaticParameters, but for simpler case, when return type is also known in designer (i.e. it does not depend on parameters)
 */
trait ServiceWithStaticParametersAndReturnType extends EagerServiceWithStaticParameters {

  def returnType: TypingResult

  def invoke(params: Map[String, Any])(implicit ec: ExecutionContext,
                                       collector: InvocationCollectors.ServiceInvocationCollector,
                                       contextId: ContextId,
                                       metaData: MetaData): Future[Any]

  override def serviceImplementation(eagerParameters: Map[String, Any], typingResult: TypingResult, metaData: MetaData): ServiceInvoker = {
    implicit val metaImplicit: MetaData = metaData
    new ServiceInvoker {

      override def invokeService(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                           collector: InvocationCollectors.ServiceInvocationCollector,
                                                           contextId: ContextId,
                                                           runMode: RunMode): Future[Any] =
        invoke(params ++ eagerParameters)

    }
  }

  override def hasOutput: Boolean = !List(Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(returnType)

  override def returnType(validationContext: ValidationContext,
                          parameters: Map[String, DefinedSingleParameter]): ValidatedNel[ProcessCompilationError, TypingResult] = Valid(returnType)
}