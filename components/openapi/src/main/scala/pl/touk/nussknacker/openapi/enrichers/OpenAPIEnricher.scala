package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.{Context, Params, ServiceInvoker}
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.util.service.AsyncExecutionTimeMeasurement
import pl.touk.nussknacker.engine.util.service.EagerServiceWithErrorSupport.HandleErrorsParamName
import pl.touk.nussknacker.engine.util.service.ReturnErrors
import pl.touk.nussknacker.http.backend.{HttpBackendProvider, LoggingAndCollectingSttpBackend}
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.enrichers.InvocationBaseUrl.determineInvocationBaseUrl
import pl.touk.nussknacker.openapi.enrichers.OpenAPIEnricher.packageName
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.SwaggerSttpService
import sttp.client3.{HttpError, SttpBackend}
import sttp.model.StatusCode

import scala.concurrent.{ExecutionContext, Future}

class OpenAPIEnricher(
    service: SwaggerService,
    extractor: ParametersExtractor,
    config: OpenAPIServicesConfig,
    clientProvider: HttpBackendProvider,
    params: Params,
    getTimeMeasurement: () => AsyncExecutionTimeMeasurement
) extends ServiceInvoker {
  private val baseUrl                 = determineInvocationBaseUrl(config.url, config.rootUrl, service.servers)
  private val codesToInterpretAsEmpty = config.codesToInterpretAsEmpty.map(StatusCode(_))
  private val swaggerHttpService      = new SwaggerSttpService(baseUrl, service, codesToInterpretAsEmpty)
  private val serviceName             = service.name.value
  private val returnErrors            = ReturnErrors.fromBoolean[AnyRef](isHandleErrorsEnabled)
  private val tags                    = Map(AsyncExecutionTimeMeasurement.serviceNameTagKey -> serviceName)

  override def invoke(context: Context)(
      implicit ec: ExecutionContext,
      collector: InvocationCollectors.ServiceInvocationCollector,
      componentUseContext: ComponentUseContext
  ): Future[AnyRef] = getTimeMeasurement().measuring(tags) {
    val fixedOrEvaluatedParams = extractor.parameterDefinition
      .map { p => p.name.value -> params.extractOrEvaluateDeclaredLazyParam[AnyRef](p.name, context) }
      .collect {
        case (name, Some(value)) => name -> value
        case (name, None)        => name -> null
      }
      .toMap
    val preparedParams = extractor.prepareParams(fixedOrEvaluatedParams)
    val invokeResult   = swaggerHttpService.invokeWithStatus(preparedParams)
    handleResult(invokeResult)
  }

  private def handleResult(
      invokeResult: Future[(AnyRef, Option[java.lang.Integer])]
  )(implicit ec: ExecutionContext): Future[AnyRef] = {
    ReturnErrors
      .handleWithStatus(
        returnErrors,
        invokeResult,
        errorDescription = errorDescription,
        errorStatusCode = extractStatusCode
      )
      .map(_.asInstanceOf[AnyRef])
  }

  implicit protected def httpBackendForEc(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector
  ): SttpBackend[Future, Any] = {
    val originalBackend: SttpBackend[Future, Any] = clientProvider.httpBackendForEc
    new LoggingAndCollectingSttpBackend(originalBackend, s"$packageName.$serviceName")
  }

  private def isHandleErrorsEnabled: Boolean = params.extractParam[Boolean](HandleErrorsParamName) match {
    case Params.ParamExtractionResult.Value(value) => value
    case _                                         => false
  }

  private def errorDescription(error: Throwable): String = {
    if (error.getCause == null) {
      s"error: ${error.getMessage}"
    } else {
      s"error: ${error.getMessage}. ${error.getCause.getMessage}"
    }
  }

  private def extractStatusCode(error: Throwable): Option[java.lang.Integer] = {
    @annotation.tailrec
    def loop(throwable: Throwable): Option[java.lang.Integer] = throwable match {
      case null                    => None
      case httpError: HttpError[_] => Some(Int.box(httpError.statusCode.code))
      case other                   => loop(other.getCause)
    }

    loop(error)
  }

}

object OpenAPIEnricher {
  private[enrichers] val packageName: String = getClass.getPackage.getName

  def apply(
      service: SwaggerService,
      config: OpenAPIServicesConfig,
      clientProvider: HttpBackendProvider,
      params: Params,
      getTimeMeasurement: () => AsyncExecutionTimeMeasurement
  ): OpenAPIEnricher = new OpenAPIEnricher(
    service,
    new ParametersExtractor(service, Map[String, () => AnyRef]()),
    config,
    clientProvider,
    params,
    getTimeMeasurement
  )

}
