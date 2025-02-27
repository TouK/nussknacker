package pl.touk.nussknacker.openapi.enrichers

import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.util.service.{EagerServiceWithStaticParametersAndReturnType, TimeMeasuringService}
import pl.touk.nussknacker.http.backend.{
  FixedAsyncHttpClientBackendProvider,
  HttpBackendProvider,
  HttpClientConfig,
  LoggingAndCollectingSttpBackend
}
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.enrichers.SwaggerEnricherCreator.determineInvocationBaseUrl
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.SwaggerSttpService
import pl.touk.nussknacker.openapi.http.backend.SharedHttpClientBackendProvider
import sttp.client3.SttpBackend
import sttp.model.StatusCode

import java.net.{MalformedURLException, URL}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class SwaggerEnricher(
    baseUrl: URL,
    swaggerService: SwaggerService,
    fixedParams: Map[String, () => AnyRef],
    httpBackendProvider: HttpBackendProvider,
    codesToInterpretAsEmpty: List[StatusCode]
) extends EagerServiceWithStaticParametersAndReturnType
    with TimeMeasuringService {

  override protected def serviceName: String = swaggerService.name.value

  private val swaggerHttpService = new SwaggerSttpService(baseUrl, swaggerService, codesToInterpretAsEmpty)

  private val parameterExtractor = new ParametersExtractor(swaggerService, fixedParams)

  implicit protected def httpBackendForEc(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector
  ): SttpBackend[Future, Any] = {
    val originalBackend: SttpBackend[Future, Any] = httpBackendProvider.httpBackendForEc
    new LoggingAndCollectingSttpBackend(originalBackend, s"${getClass.getPackage.getName}.$serviceName")
  }

  override def parameters: List[Parameter] = parameterExtractor.parameterDefinition

  override def hasOutput: Boolean = true

  override def returnType: typing.TypingResult =
    swaggerService.responseSwaggerType.map(_.typingResult).getOrElse(Typed[Unit])

  override def invoke(eagerParameters: Map[ParameterName, Any])(
      implicit ec: ExecutionContext,
      collector: ServiceInvocationCollector,
      contextId: ContextId,
      metaData: MetaData,
      componentUseCase: ComponentUseCase
  ): Future[AnyRef] =
    measuring {
      swaggerHttpService.invoke(
        parameterExtractor.prepareParams(eagerParameters.map { case (p, value) => (p.value, value) })
      )
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

  def create(
      definitionUrl: URL,
      rootUrl: Option[URL],
      swaggerService: SwaggerService,
      fixedParams: Map[String, () => AnyRef],
      codesToInterpretAsEmpty: List[StatusCode]
  ): SwaggerEnricher = {
    val baseUrl = determineInvocationBaseUrl(definitionUrl, rootUrl, swaggerService.servers)
    new SwaggerEnricher(baseUrl, swaggerService, fixedParams, httpBackendProvider, codesToInterpretAsEmpty)
  }

}

/*
  We want to be able to use OpenAPI integration both in Flink and in other engines.
  This class should work if classpath contains either Flink or not, that's why
  we use class name
 */
object SwaggerEnricherCreator {

  def apply(httpClientConfig: HttpClientConfig): SwaggerEnricherCreator = {
    val isFlinkBased = Try(
      getClass.getClassLoader
        .loadClass("org.apache.flink.streaming.api.environment.StreamExecutionEnvironment")
    ).isSuccess
    val backendProvider = if (isFlinkBased) {
      new SharedHttpClientBackendProvider(httpClientConfig)
    } else {
      // TODO: figure out how to create client only once and enable its closing. Also: do we want to pass processId here?
      // Should client be one per engine deployment, or per scenario?
      val httpClient = new DefaultAsyncHttpClient(httpClientConfig.toAsyncHttpClientConfig(None).build())
      new FixedAsyncHttpClientBackendProvider(httpClient)
    }
    new SwaggerEnricherCreator(backendProvider)
  }

  private[enrichers] def determineInvocationBaseUrl(
      definitionUrl: URL,
      rootUrl: Option[URL],
      serversFromDefinition: List[String]
  ): URL = {
    def relativeToDefinitionUrl(serversUrlPart: String): URL = {
      try {
        new URL(definitionUrl, serversUrlPart)
      } catch {
        case _: MalformedURLException =>
          new URL(serversUrlPart)
      }
    }
    // Regarding https://spec.openapis.org/oas/v3.1.0#fixed-fields default server url is /
    rootUrl.getOrElse(relativeToDefinitionUrl(serversFromDefinition.headOption.getOrElse("/")))
  }

}
