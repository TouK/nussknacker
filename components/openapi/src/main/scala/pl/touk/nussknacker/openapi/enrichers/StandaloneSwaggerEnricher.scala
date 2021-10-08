package pl.touk.nussknacker.openapi.enrichers

import pl.touk.nussknacker.engine.standalone.utils.service.TimeMeasuringService
import pl.touk.nussknacker.openapi.SwaggerService
import sttp.client.SttpBackend

import java.net.URL
import scala.concurrent.{ExecutionContext, Future}

class StandaloneSwaggerEnricher(rootUrl: Option[URL], swaggerService: SwaggerService,
                           fixedParams: Map[String, () => AnyRef],
                           backendForEc: ExecutionContext => SttpBackend[Future, Nothing, Nothing])
  extends BaseSwaggerEnricher(rootUrl, swaggerService, fixedParams) with TimeMeasuringService {

  override implicit protected def httpBackendForEc(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, Nothing] = backendForEc(ec)

}