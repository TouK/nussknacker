package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.{Context, Params, ServiceInvoker}
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors
import pl.touk.nussknacker.engine.util.service.AsyncExecutionTimeMeasurement
import pl.touk.nussknacker.http.backend.HttpBackendProvider
import pl.touk.nussknacker.openapi.{OpenAPIServicesConfig, SwaggerService}
import pl.touk.nussknacker.openapi.enrichers.InvocationBaseUrl.determineInvocationBaseUrl
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.SwaggerSttpService
import sttp.client3.SttpBackend
import sttp.model.StatusCode

import scala.concurrent.{ExecutionContext, Future}

class SwaggerEnricher(
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
  private val preparedParams = extractor
    .prepareParams(params.nameToValueMap.map { case (p, value) => (p.value, value) })
  private val tags = Map(AsyncExecutionTimeMeasurement.serviceNameTagKey -> service.name.value)

  override def invoke(context: Context)(
      implicit ec: ExecutionContext,
      collector: InvocationCollectors.ServiceInvocationCollector,
      componentUseContext: ComponentUseContext
  ): Future[AnyRef] = getTimeMeasurement().measuring(tags) {
    implicit val httpClient: SttpBackend[Future, Any] = clientProvider.httpBackendForEc
    swaggerHttpService.invoke(preparedParams)
  }

}

object SwaggerEnricher {

  def apply(
      service: SwaggerService,
      config: OpenAPIServicesConfig,
      clientProvider: HttpBackendProvider,
      params: Params,
      getTimeMeasurement: () => AsyncExecutionTimeMeasurement
  ): SwaggerEnricher = new SwaggerEnricher(
    service,
    new ParametersExtractor(service, Map[String, () => AnyRef]()),
    config,
    clientProvider,
    params,
    getTimeMeasurement
  )

}
