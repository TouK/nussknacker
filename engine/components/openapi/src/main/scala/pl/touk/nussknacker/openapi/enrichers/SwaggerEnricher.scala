package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.api.definition.{Parameter, ServiceWithExplicitMethod}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ContextId, JobData, MetaData}
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.SwaggerSttpService
import pl.touk.nussknacker.openapi.http.backend.{CacheableHttpBackend, HttpClientConfig}

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class SwaggerEnricher(rootUrl: Option[URL], swaggerService: SwaggerService,
                      fixedParams: Map[String, () => AnyRef],
                      httpClientConfig: HttpClientConfig) extends ServiceWithExplicitMethod with TimeMeasuringService {

  private val swaggerHttpService = new SwaggerSttpService(rootUrl, swaggerService)

  private val parameterExtractor = new ParametersExtractor(swaggerService, fixedParams)

  private var httpBackend: CacheableHttpBackend.ShutdownableClient = _

  override def parameterDefinition: List[Parameter] =
    parameterExtractor.parameterDefinition

  override def returnType: typing.TypingResult =
    swaggerService.responseSwaggerType.map(_.typingResult)
      .getOrElse(Typed[Unit])

  override def invokeService(params: List[AnyRef])(implicit ec: ExecutionContext,
                                                   collector: ServiceInvocationCollector,
                                                   metaData: MetaData,
                                                   contextId: ContextId): Future[AnyRef] = measuring {
    swaggerHttpService.invoke(parameterExtractor.prepareParams(params))(httpBackend.client)
  }

  override def open(jobData: JobData): Unit = {
    httpBackend = CacheableHttpBackend.retrieveClient(httpClientConfig, jobData.metaData, serviceName)
  }

  override protected def serviceName: String = swaggerService.name

  override def close(): Unit = synchronized {
    httpBackend.shutdown()
  }

}
