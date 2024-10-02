package pl.touk.nussknacker.http.enricher

import cats.implicits._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  DefinedLazyParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.http.HttpEnricherConfig
import pl.touk.nussknacker.http.client.HttpClientProvider
import pl.touk.nussknacker.http.enricher.HttpEnricherFactory._
import sttp.model.Method

class HttpEnricherFactory(enricherConfig: HttpEnricherConfig)
    extends EagerService
    with SingleInputDynamicComponent[ServiceInvoker] {

  case class EagerState(method: Method)
  override type State = EagerState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    initialStep orElse finalStep(context, dependencies)
  }

  private def initialStep(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(parameters =
      urlParamDeclaration.createParameter() :: httpMethodParamDeclataion
        .createParameter() :: headersParamDeclaration.createParameter() :: Nil
    )
  }

  private def finalStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (`UrlParamName`, DefinedLazyParameter(_)) ::
          (`HTTPMethodParamName`, DefinedEagerParameter(httpMethod: String, _)) ::
          (`HeadersParamName`, DefinedLazyParameter(_)) :: Nil,
          _
        ) =>
      val outName = OutputVariableNameDependency.extract(dependencies)

      val method = Method
        .safeApply(httpMethod)
        .toValidated
        .leftMap(err => new CustomNodeError(nodeId.id, err, Some(HTTPMethodParamName)))
        .toValidatedNel

      FinalResults.forValidation(
        context,
        method.swap.map(_.toList).getOrElse(List.empty),
        method.toOption.map(EagerState)
      )(ctx => ctx.withVariable(outName, outputTypingResult, Some(ParameterName(OutputVar.CustomNodeFieldName))))
  }

  private val outputTypingResult = Typed.record(
    List(
      "statusCode" -> Typed[Int],
      "statusText" -> Typed[String],
      "headers"    -> Typed.typedClass[java.util.Map[String, String]],
      "body"       -> Unknown
    )
  )

  private val httpBeProvider = HttpClientProvider.getBackendProvider(enricherConfig.httpClientConfig)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[EagerState]
  ): ServiceInvoker = {
    val state =
      finalState.getOrElse(throw new IllegalStateException("Expected state at end of transformation. Got none."))
    new HttpEnricher(
      httpBeProvider,
      params,
      state.method,
      enricherConfig.rootUrl,
      enricherConfig.security.getOrElse(List.empty)
    )
  }

  override def open(runtimeContext: EngineRuntimeContext): Unit = {
    super.open(runtimeContext)
    httpBeProvider.open(runtimeContext)
  }

  override def close(): Unit = {
    super.close()
    httpBeProvider.close()
  }

  override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)
}

object HttpEnricherFactory {

  private val UrlParamName: ParameterName = ParameterName("URL")

  private val urlParamDeclaration = ParameterDeclaration
    .lazyMandatory[String](UrlParamName)
    .withCreator()

  val urlParamExtractor: (Context, Params) => String = (context: Context, params: Params) =>
    urlParamDeclaration.extractValueUnsafe(params).evaluate(context)

  private val HTTPMethodParamName: ParameterName = ParameterName("HTTP Method")

  private val httpMethodParamDeclataion = ParameterDeclaration
    .mandatory[String](HTTPMethodParamName)
    .withCreator(modify =
      _.copy(editor =
        Some(
          FixedValuesParameterEditor(
            List(
              FixedExpressionValue("'GET'", "GET"),
              FixedExpressionValue("'POST'", "POST"),
              FixedExpressionValue("'PUT'", "PUT"),
              FixedExpressionValue("'DELETE'", "DELETE"),
              FixedExpressionValue("'HEAD'", "HEAD"),
              FixedExpressionValue("'CONNECT'", "CONNECT"),
              FixedExpressionValue("'OPTIONS'", "OPTIONS"),
              FixedExpressionValue("'TRACE'", "TRACE"),
              FixedExpressionValue("'PATCH'", "PATCH"),
            )
          )
        )
      )
    )

  private val HeadersParamName: ParameterName = ParameterName("Headers")

  private val headersParamDeclaration =
    ParameterDeclaration.lazyOptional[java.util.Map[String, String]](HeadersParamName).withCreator()

  val headerParamExtractor: (Context, Params) => java.util.Map[String, String] = (context: Context, params: Params) =>
    headersParamDeclaration.extractValueUnsafe(params).evaluate(context)

  /*
   TODO: add params:
    1. Body
    2. Input-body Media-Type
    3. Query params
   */
}
