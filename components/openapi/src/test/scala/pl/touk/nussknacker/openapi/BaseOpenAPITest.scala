package pl.touk.nussknacker.openapi

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import org.apache.commons.io.IOUtils
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.process.ComponentUseContext
import pl.touk.nussknacker.engine.util.runtimecontext.TestEngineRuntimeContext
import pl.touk.nussknacker.engine.util.service.AsyncExecutionTimeMeasurement
import pl.touk.nussknacker.openapi.enrichers.SwaggerEnricher
import pl.touk.nussknacker.openapi.parser.{ServiceParseError, SwaggerParser}
import sttp.client3.SttpBackend

import java.net.URL
import java.nio.charset.StandardCharsets
import scala.concurrent.{ExecutionContext, Future}

trait BaseOpenAPITest {

  protected val baseConfig: OpenAPIServicesConfig = OpenAPIServicesConfig(new URL("http://foo"))

  implicit val componentUseContext: ComponentUseContext = ComponentUseContext.LiveRuntime(None)
  implicit val metaData: MetaData                       = MetaData("testProc", StreamMetaData())
  implicit val context: Context                         = Context("testContextId", Map.empty)
  protected val jobData      = JobData(metaData, ProcessVersion.empty.copy(processName = metaData.name))
  private val runtimeContext = TestEngineRuntimeContext(jobData)

  protected def parseServicesFromResource(
      name: String,
      config: OpenAPIServicesConfig = baseConfig
  ): List[Validated[ServiceParseError, SwaggerService]] = {
    SwaggerParser.parse(parseResource(name), config)
  }

  protected def parseServicesFromResourceUnsafe(
      name: String,
      config: OpenAPIServicesConfig = baseConfig
  ): List[SwaggerService] = {
    parseServicesFromResource(name, config).map {
      case Valid(service) => service
      case Invalid(e)     => throw new AssertionError(s"Parse failure: $e")
    }
  }

  protected def parseResource(name: String): String =
    IOUtils.toString(getClass.getResourceAsStream(s"/swagger/$name"), StandardCharsets.UTF_8)

  protected def parseToEnrichers(
      resource: String,
      backend: SttpBackend[Future, Any],
      config: OpenAPIServicesConfig = baseConfig,
      params: Map[ParameterName, Any] = Map.empty,
      getTimeMeasurement: () => AsyncExecutionTimeMeasurement = () =>
        new AsyncExecutionTimeMeasurement(runtimeContext, "openAPI", Map.empty)
  ): Map[ServiceName, ServiceInvoker] =
    parseServicesFromResourceUnsafe(resource, config).map { service =>
      service.name -> SwaggerEnricher(
        service = service,
        config = config,
        clientProvider = (_: ExecutionContext) => backend,
        params = Params(params),
        getTimeMeasurement = getTimeMeasurement
      )
    }.toMap

}
