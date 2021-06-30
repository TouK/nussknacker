package pl.touk.nussknacker.openapi.enrichers

import org.asynchttpclient.DefaultAsyncHttpClient
import pl.touk.nussknacker.engine.api.definition.{Parameter, ServiceWithExplicitMethod}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{ContextId, MetaData}
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService
import pl.touk.nussknacker.openapi.SwaggerService
import pl.touk.nussknacker.openapi.extractor.ParametersExtractor
import pl.touk.nussknacker.openapi.http.SwaggerSttpService
import pl.touk.nussknacker.openapi.http.backend.HttpClientConfig
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

abstract class BaseSwaggerEnricher(rootUrl: Option[URL], swaggerService: SwaggerService,
                                   fixedParams: Map[String, () => AnyRef]) extends ServiceWithExplicitMethod with GenericTimeMeasuringService {

  override protected def serviceName: String = swaggerService.name

  private val swaggerHttpService = new SwaggerSttpService(rootUrl, swaggerService)

  private val parameterExtractor = new ParametersExtractor(swaggerService, fixedParams)

  implicit protected def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing]

  override def parameterDefinition: List[Parameter] =
    parameterExtractor.parameterDefinition

  override def returnType: typing.TypingResult =
    swaggerService.responseSwaggerType.map(_.typingResult)
      .getOrElse(Typed[Unit])

  override def invokeService(params: List[AnyRef])(implicit ec: ExecutionContext,
                                                   collector: ServiceInvocationCollector,
                                                   metaData: MetaData,
                                                   contextId: ContextId): Future[AnyRef] = measuring {
    swaggerHttpService.invoke(parameterExtractor.prepareParams(params))
  }

}

trait BaseSwaggerEnricherCreator {

  def create(rootUrl: Option[URL],
             swaggerService: SwaggerService,
             fixedParams: Map[String, () => AnyRef]): BaseSwaggerEnricher

}

object BaseSwaggerEnricherCreator {

  def apply(httpClientConfig: HttpClientConfig): BaseSwaggerEnricherCreator = {
    val isFlinkBased = Try(getClass.getClassLoader
      .loadClass("org.apache.flink.streaming.api.environment.StreamExecutionEnvironment")).isSuccess
    val isStandaloneBased = Try(getClass.getClassLoader
      .loadClass("pl.touk.nussknacker.engine.standalone.api.StandaloneContext")).isSuccess
    if (isFlinkBased) {
      return new BaseSwaggerEnricherCreator {
        override def create(rootUrl: Option[URL], swaggerService: SwaggerService, fixedParams: Map[String, () => AnyRef]): BaseSwaggerEnricher =
          new FlinkSwaggerEnricher(rootUrl, swaggerService, fixedParams, httpClientConfig)
      }
    }
    if (isStandaloneBased) {
      return new BaseSwaggerEnricherCreator {

        lazy val asyncHttpClient = new DefaultAsyncHttpClient(httpClientConfig.toAsyncHttpClientConfig(None).build())

        override def create(rootUrl: Option[URL], swaggerService: SwaggerService, fixedParams: Map[String, () => AnyRef]): BaseSwaggerEnricher =
          new StandaloneSwaggerEnricher(rootUrl, swaggerService, fixedParams, AsyncHttpClientFutureBackend.usingClient(asyncHttpClient)(_))
      }
    }
    throw new IllegalArgumentException("Either Flink API or Standalone API should be on classpath.")
  }

}