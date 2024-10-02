package pl.touk.nussknacker.http.enricher

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.context.transformation.{
  DefinedEagerParameter,
  NodeDependencyValue,
  SingleInputDynamicComponent
}
import pl.touk.nussknacker.engine.api.context.{OutputVar, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.http.backend.HttpClientConfig
import pl.touk.nussknacker.http.client.HttpClientProvider
import sttp.model.{Header, Method, Uri}

import scala.jdk.CollectionConverters._

// TODO: add configurable root URL and security
class HttpEnricherFactory(httpConfig: HttpClientConfig)
    extends EagerService
    with SingleInputDynamicComponent[ServiceInvoker] {

  private val UrlParamName: ParameterName = ParameterName("URL")

  private val urlParamDeclaration = ParameterDeclaration
    .mandatory[String](UrlParamName)
    .withCreator(modify =
      _.copy(
        editor = Some(SpelTemplateParameterEditor)
      )
    )

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

  private val headersParamDeclaration = ParameterDeclaration
    .optional[String](HeadersParamName)
    .withCreator(modify =
      _.copy(
        typ = Typed.genericTypeClass(
          classOf[java.util.Map[_, _]],
          List(Typed[String], Typed[String])
        )
      )
    )

  override type State = RequestData

  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    initialStep orElse inferSchemaStep(context, dependencies)
  }

  private def initialStep(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = { case TransformationStep(Nil, _) =>
    NextParameters(parameters =
      urlParamDeclaration.createParameter() :: httpMethodParamDeclataion
        .createParameter() :: headersParamDeclaration.createParameter() :: Nil
    )
  }

  private def inferSchemaStep(context: ValidationContext, dependencies: List[NodeDependencyValue])(
      implicit nodeId: NodeId
  ): ContextTransformationDefinition = {
    case TransformationStep(
          (`UrlParamName`, DefinedEagerParameter(url: String, _)) ::
          (`HTTPMethodParamName`, DefinedEagerParameter(httpMethod: String, _)) ::
          (`HeadersParamName`, DefinedEagerParameter(headersFromParam, _)) :: Nil,
          _
        ) if headersFromParam.isInstanceOf[java.util.Map[_, _]] || headersFromParam == null => {

      val outName = OutputVariableNameDependency.extract(dependencies)

      // TODO: add correct URL validation
      val uri = Uri.parse(url).leftMap(err => new CustomNodeError(nodeId.id, err, Some(UrlParamName))).toValidatedNel

      val method = Method
        .safeApply(httpMethod)
        .toValidated
        .leftMap(err => new CustomNodeError(nodeId.id, err, Some(HTTPMethodParamName)))
        .toValidatedNel

      // We interpret null as empty list to enable sending no headers
      val headers = headersFromParam match {
        case map: java.util.Map[_, _] => extractHeaders(map.asScala.toMap)
        case null                     => Valid(List.empty)
        case _                        => throw new IllegalStateException("Invalid state")
      }

      val result = (uri, method, headers).mapN { (validUri, validMethod, validHeaders) =>
        {
          RequestData(validUri, validMethod, validHeaders)
        }
      }

      FinalResults.forValidation(
        context,
        result.fold(errors => errors.toList, _ => List.empty),
        result.toOption
      )(ctx => ctx.withVariable(outName, outputTypingResult, Some(ParameterName(OutputVar.CustomNodeFieldName))))
    }

    case TransformationStep(
          (`UrlParamName`, DefinedEagerParameter(_: String, _)) ::
          (`HTTPMethodParamName`, DefinedEagerParameter(_: String, _)) ::
          (`HeadersParamName`, DefinedEagerParameter(headersFromParam, _)) :: Nil,
          _
        ) if !headersFromParam.isInstanceOf[java.util.Map[_, _]] => {
      val outName = OutputVariableNameDependency.extract(dependencies)
      FinalResults.forValidation(
        context,
        // TODO: better error
        List(new CustomNodeError(nodeId.id, "Wrong parameter type", Some(HeadersParamName))),
        None
      )(ctx => ctx.withVariable(outName, outputTypingResult, Some(ParameterName(OutputVar.CustomNodeFieldName))))
    }
  }

  private def extractHeaders(
      map: Map[_, _]
  )(implicit nodeId: NodeId): ValidatedNel[CustomNodeError, List[Header]] = {
    map.toList match {
      case Nil => Valid(List.empty)
      case list if list.forall { case (k, v) => k.isInstanceOf[String] && v.isInstanceOf[String] } =>
        val mapOfStrings = map.asInstanceOf[Map[String, String]]
        mapOfStrings
          .map { case (k, v) =>
            Header(k, v)
          }
          .toList
          .validNel
      case _ =>
        Invalid(
          new CustomNodeError(nodeId.id, "Wrong parameter type - not a list of maps of strings", Some(HeadersParamName))
        ).toValidatedNel
    }
  }

  private val outputTypingResult = Typed.record(
    List(
      "statusCode" -> Typed[Int],
      "statusText" -> Typed[String],
      "headers"    -> Typed.typedClass[java.util.Map[String, String]],
      "body"       -> Unknown
    )
  )

  private val httpBeProvider = HttpClientProvider.getBackendProvider(httpConfig)

  override def implementation(
      params: Params,
      dependencies: List[NodeDependencyValue],
      finalState: Option[RequestData]
  ): ServiceInvoker = {
    val state =
      finalState.getOrElse(throw new IllegalStateException("Expected state at end of transformation. Got none."))
    new HttpEnricher(httpBeProvider, state)
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
