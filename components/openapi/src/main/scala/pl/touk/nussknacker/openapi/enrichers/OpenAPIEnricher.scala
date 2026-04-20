package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.{Context, Params, ServiceInvoker}
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.util.service.AsyncExecutionTimeMeasurement
import pl.touk.nussknacker.engine.util.service.EagerServiceWithErrorSupport.isHandleErrorsEnabled
import pl.touk.nussknacker.engine.util.service.ReturnErrors
import pl.touk.nussknacker.http.backend.{HttpBackendProvider, HttpStatusCodeExtractor, LoggingAndCollectingSttpBackend}
import pl.touk.nussknacker.http.backend.HttpStatusCodeExtractor.handleHttpResult
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.enrichers.InvocationBaseUrl.determineInvocationBaseUrl
import pl.touk.nussknacker.openapi.enrichers.OpenAPIEnricher.packageName
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.SwaggerSttpService
import sttp.client3.SttpBackend
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
  private val returnErrors            = ReturnErrors.fromBoolean[AnyRef](isHandleErrorsEnabled(params))
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
    handleHttpResult(returnErrors, invokeResult)
  }

  implicit protected def httpBackendForEc(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector
  ): SttpBackend[Future, Any] = {
    val originalBackend: SttpBackend[Future, Any] = clientProvider.httpBackendForEc
    new LoggingAndCollectingSttpBackend(originalBackend, s"$packageName.$serviceName")
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
