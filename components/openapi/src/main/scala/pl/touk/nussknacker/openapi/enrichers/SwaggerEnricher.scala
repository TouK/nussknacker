package pl.touk.nussknacker.openapi.enrichers

import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ContextId, JobData, MetaData}
import pl.touk.nussknacker.engine.util.service.{TimeMeasuringService, ServiceWithStaticParametersAndReturnType}
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.SwaggerSttpService
import pl.touk.nussknacker.openapi.http.backend.{FixedAsyncHttpClientBackendProvider, HttpBackendProvider, HttpClientConfig, SharedHttpClientBackendProvider}
import sttp.client.SttpBackend

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class SwaggerEnricher(rootUrl: Option[URL], swaggerService: SwaggerService,
                      fixedParams: Map[String, () => AnyRef],
                      httpBackendProvider: HttpBackendProvider) extends ServiceWithStaticParametersAndReturnType with TimeMeasuringService {

  override protected def serviceName: String = swaggerService.name

  private val swaggerHttpService = new SwaggerSttpService(rootUrl, swaggerService)

  private val parameterExtractor = new ParametersExtractor(swaggerService, fixedParams)

  implicit protected def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing] = httpBackendProvider.httpBackendForEc

  override def parameters: List[Parameter] = parameterExtractor.parameterDefinition

  override def hasOutput: Boolean = true

  override def returnType: typing.TypingResult = swaggerService.responseSwaggerType.map(_.typingResult).getOrElse(Typed[Unit])

  override def invoke(params: Map[String, Any])
                     (implicit ec: ExecutionContext, collector: ServiceInvocationCollector, contextId: ContextId, metaData: MetaData): Future[AnyRef] =
    measuring {
      swaggerHttpService.invoke(parameterExtractor.prepareParams(params))
    }

  override def open(runtimeContext: EngineRuntimeContext): Unit = {
    super.open(runtimeContext)
    httpBackendProvider.open(runtimeContext)
  }

  override def close(): Unit = {
    super.close()
    httpBackendProvider.close()
  }
}

class SwaggerEnricherCreator(httpBackendProvider: HttpBackendProvider) {

  def create(rootUrl: Option[URL],
             swaggerService: SwaggerService,
             fixedParams: Map[String, () => AnyRef]): SwaggerEnricher = {
    new SwaggerEnricher(rootUrl, swaggerService, fixedParams, httpBackendProvider)
  }

}

/*
  We want to be able to use OpenAPI integration both in Flink and in other engines.
  This class should work if classpath contains either Flink or not, that's why
  we use class name
 */
object SwaggerEnricherCreator {

  def apply(httpClientConfig: HttpClientConfig): SwaggerEnricherCreator = {
    val isFlinkBased = Try(getClass.getClassLoader
      .loadClass("org.apache.flink.streaming.api.environment.StreamExecutionEnvironment")).isSuccess
    val backendProvider = if (isFlinkBased) {
      new SharedHttpClientBackendProvider(httpClientConfig)
    } else {
      //TODO: figure out how to create client only once and enable its closing. Also: do we want to pass processId here?
      //Should client be one per engine deployment, or per scenario?
      val httpClient = new DefaultAsyncHttpClient(httpClientConfig.toAsyncHttpClientConfig(None).build())
      new FixedAsyncHttpClientBackendProvider(httpClient)
    }
    new SwaggerEnricherCreator(backendProvider)
  }

}
