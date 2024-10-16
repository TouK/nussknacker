package pl.touk.nussknacker.http.enricher

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
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.http.HttpEnricherConfig
import pl.touk.nussknacker.http.client.HttpClientProvider
import pl.touk.nussknacker.http.enricher.HttpEnricher.{BodyType, HttpMethod, buildURL}
import pl.touk.nussknacker.http.enricher.HttpEnricherFactory.{
  BodyParamExtractor,
  BodyTypeParamExtractor,
  TransformationState
}
import pl.touk.nussknacker.http.enricher.HttpEnricherParameters._
import sttp.model.QueryParams

class HttpEnricherFactory(val config: HttpEnricherConfig)
    extends EagerService
    with SingleInputDynamicComponent[ServiceInvoker] {

  override type State = TransformationState

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    initializeParams orElse addBodyParam orElse finalStep(context, dependencies)
  }

  private val initializeParams: ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(
      parameters = UrlParam.declaration(config.rootUrl).createParameter() ::
        QueryParamsParam.declaration.createParameter() ::
        MethodParam.declaration(config.allowedMethods).createParameter() ::
        HeadersParam.declaration.createParameter() ::
        BodyTypeParam.declaration.createParameter() :: Nil,
      state = Some(TransformationState.InitialState)
    )
  }

  private val addBodyParam: ContextTransformationDefinition = {
    case TransformationStep(
          BodyTypeParamExtractor(bodyTypeParamValue),
          Some(TransformationState.InitialState)
        ) => {
      val bodyType = BodyType.values
        .find(_.name == bodyTypeParamValue)
        .getOrElse(throw new IllegalStateException("Invalid body type parameter value."))
      NextParameters(
        parameters = BodyParam.declaration(bodyType).map(_.createParameter()).toList,
        state = Some(TransformationState.BodyTypeDeclared(bodyType))
      )
    }
  }

  private def finalStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (UrlParam.name, DefinedLazyParameter(lazyUrlParam)) ::
          (QueryParamsParam.name, _) ::
          (MethodParam.name, DefinedEagerParameter(httpMethod: String, _)) ::
          (HeadersParam.name, _) ::
          (BodyTypeParam.name, _) ::
          parametersTail,
          Some(TransformationState.BodyTypeDeclared(bodyType))
        ) =>
      val outName = OutputVariableNameDependency.extract(dependencies)

      val method = HttpMethod.values
        .find(_.name == httpMethod)
        .getOrElse(throw new IllegalStateException("Invalid body type parameter value."))

      val compileTimeUrlValidationErrorOpt = lazyUrlParam.valueOpt.flatMap {
        case url: String =>
          buildURL(config.rootUrl, url, QueryParams()).swap.toOption.map(ex =>
            CustomNodeError(s"Invalid URL: ${ex.cause.getMessage}", Some(UrlParam.name))
          )
        case _ => None
      }

      val requestBodyTypingResult = bodyType match {
        case BodyType.None => typing.TypedNull
        case nonEmptyBodyType =>
          parametersTail match {
            case BodyParamExtractor(typ) => typ
            case _ =>
              throw new IllegalStateException(
                s"Expected body param based on body type parameter ${nonEmptyBodyType.name}. Got none."
              )
          }
      }

      FinalResults.forValidation(
        context,
        compileTimeUrlValidationErrorOpt.toList,
        Some(TransformationState.FinalState(bodyType, method))
      )(ctx =>
        ctx.withVariable(
          outName,
          HttpEnricherOutput.typingResult(requestBodyTypingResult),
          Some(ParameterName(OutputVar.CustomNodeFieldName))
        )
      )
  }

  private val httpBeProvider = HttpClientProvider.getBackendProvider(config.httpClientConfig)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[TransformationState]
  ): ServiceInvoker = {
    finalState match {
      case Some(TransformationState.FinalState(bodyType, method)) =>
        new HttpEnricher(
          httpBeProvider,
          params,
          method,
          bodyType,
          config.rootUrl,
          config.security.getOrElse(List.empty)
        )
      case other => throw new IllegalStateException(s"Expected final state at end of transformation. Got $other")
    }
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
  sealed trait TransformationState

  private[http] object TransformationState {
    case object InitialState                                      extends TransformationState
    case class BodyTypeDeclared(bodyType: BodyType)               extends TransformationState
    case class FinalState(bodyType: BodyType, method: HttpMethod) extends TransformationState
  }

  private object BodyTypeParamExtractor {

    def unapply(params: List[(_, _)]): Option[String] = {
      params.collectFirst {
        case (HttpEnricherParameters.BodyTypeParam.name, DefinedEagerParameter(bodyTypeParamValue: String, _)) =>
          bodyTypeParamValue
      }
    }

  }

  private object BodyParamExtractor {

    def unapply(params: List[(_, _)]): Option[TypingResult] = {
      params.collectFirst {
        case (HttpEnricherParameters.BodyParam.name, DefinedLazyParameter(bodyTypingResult: TypingResult)) =>
          bodyTypingResult
      }
    }

  }

}
