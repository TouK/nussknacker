package pl.touk.nussknacker.engine.util.service

import cats.data.Validated.Valid
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.ServiceLogic.{EvaluatedParams, ParamsEvaluator, RunContext}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedSingleParameter,
  NodeDependencyValue,
  SingleInputGenericNodeTransformation,
  WithStaticParameters
}
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{
  NodeDependency,
  OutputVariableNameDependency,
  Parameter,
  TypedNodeDependency
}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}

import scala.concurrent.{ExecutionContext, Future}
import scala.runtime.BoxedUnit

/*
  This is helper trait for creating Service which has parameter definitions fixed in designer (i.e. no parameters depending on each other)
  but parameter definitions which are not fixed/known at compile time. Good example are services which take parameter
  list from external source (e.g. configuration, OpenAPI definition, database).
  For dynamic parameters use SingleInputGenericNodeTransformation, for parameters known at compile time - use @MethodToInvoke
 */
trait EagerServiceWithStaticParameters
    extends EagerService
    with SingleInputGenericNodeTransformation[ServiceLogic]
    with WithStaticParameters {

  override type State = TypingResult

  private val metaData = TypedNodeDependency[MetaData]

  override def staticParameters: List[Parameter] = parameters

  def parameters: List[Parameter]

  def hasOutput: Boolean

  def serviceLogic(
      eagerParameters: Map[String, Any],
      typingResult: TypingResult,
      metaData: MetaData
  ): ServiceLogic

  def returnType(
      validationContext: ValidationContext,
      parameters: Map[String, DefinedSingleParameter]
  ): ValidatedNel[ProcessCompilationError, TypingResult]

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): NodeTransformationDefinition = {
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

  override def runLogic(
      params: Map[String, Any],
      dependencies: List[NodeDependencyValue],
      finalState: Option[TypingResult]
  ): ServiceLogic = {
    serviceLogic(
      params.filterNot(_._2.isInstanceOf[LazyParameter[_]]),
      finalState.getOrElse(Unknown),
      metaData.extract(dependencies)
    )
  }

}

/*
  Like in EagerServiceWithStaticParameters, but for simpler case, when return type is also known in designer (i.e. it does not depend on parameters)
 */
trait EagerServiceWithStaticParametersAndReturnType extends EagerServiceWithStaticParameters {

  def returnType: TypingResult

  // TODO: This method should be removed - instead, developers should deliver it's own ServiceInvoker to avoid
  //       mixing implementation logic with definition logic. Before that we should fix EagerService Lifecycle handling.
  //       See notice next to EagerService
  def runServiceLogic(
      paramsEvaluator: ParamsEvaluator
  )(implicit runContext: RunContext, metaData: MetaData, executionContext: ExecutionContext): Future[Any]

  override def serviceLogic(
      eagerParameters: Map[String, Any],
      typingResult: TypingResult,
      metaData: MetaData
  ): ServiceLogic = {
    implicit val metaImplicit: MetaData = metaData
    new ServiceLogic {

      override def run(
          paramsEvaluator: ParamsEvaluator
      )(implicit context: RunContext, ec: ExecutionContext): Future[Any] = {
        runServiceLogic(
          paramsEvaluator = (additionalVariables: Map[String, Any]) => {
            new EvaluatedParams(paramsEvaluator.evaluate(additionalVariables).allRaw ++ eagerParameters)
          }
        )
      }
    }
  }

  override def hasOutput: Boolean = !List(Typed[Void], Typed[Unit], Typed[BoxedUnit]).contains(returnType)

  override def returnType(
      validationContext: ValidationContext,
      parameters: Map[String, DefinedSingleParameter]
  ): ValidatedNel[ProcessCompilationError, TypingResult] = Valid(returnType)

}
