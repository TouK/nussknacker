package pl.touk.nussknacker.ui.server

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.http.scaladsl.{Http, HttpsConnectionContext}
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import fr.davit.pekko.http.metrics.core.HttpMetrics._
import fr.davit.pekko.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.pekko.http.metrics.dropwizard.{DropwizardRegistry, DropwizardSettings}
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.security.ssl.{HttpsConnectionContextFactory, SslConfigParser}

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NussknackerHttpServer(routeProvider: RouteProvider[Route], system: ActorSystem) extends LazyLogging {

  private implicit val systemImplicit: ActorSystem                = system
  private implicit val executionContextImplicit: ExecutionContext = system.dispatcher

  def start(designerConfig: DesignerConfig, metricRegistry: MetricRegistry): Resource[IO, Unit] = {
    for {
      route <- routeProvider.createRoute(designerConfig)
      _     <- createPekkoHttpBinding(designerConfig.rawConfig, route, metricRegistry)
    } yield {
      RouteInterceptor.set(route)
    }
  }

  private def createPekkoHttpBinding(
      config: ConfigWithUnresolvedVersion,
      route: Route,
      metricsRegistry: MetricRegistry
  ) = {
    def createServer() = IO.fromFuture {
      IO {
        val interface: String = config.resolved.getString("http.interface")
        val port: Int         = config.resolved.getInt("http.port")

        val bindingResultF = SslConfigParser.sslEnabled(config.resolved) match {
          case Some(keyStoreConfig) =>
            bindHttps(
              interface,
              port,
              HttpsConnectionContextFactory.createServerContext(keyStoreConfig),
              route,
              metricsRegistry
            )
          case None =>
            bindHttp(interface, port, route, metricsRegistry)
        }
        bindingResultF
          .onComplete {
            case Success(bindingResult) =>
              logger.info(s"Nussknacker designer started on ${interface}:${bindingResult.localAddress.getPort}")
            case Failure(exception) =>
              logger.error(s"Nussknacker designer cannot start", exception)
          }
        bindingResultF
      }
    }
    def shutdownServer(binding: Http.ServerBinding) =
      IO.fromFuture(IO(binding.terminate(10 seconds))).map(_ => ())

    Resource.make(acquire = createServer())(release = shutdownServer)
  }

  private def bindHttp(
      interface: String,
      port: Int,
      route: Route,
      metricsRegistry: MetricRegistry
  ): Future[Http.ServerBinding] = {
    Http()
      .newMeteredServerAt(
        interface = interface,
        port = port,
        prepareHttpMetricRegistry(metricsRegistry)
      )
      .bind(route)
  }

  private def bindHttps(
      interface: String,
      port: Int,
      httpsContext: HttpsConnectionContext,
      route: Route,
      metricsRegistry: MetricRegistry
  ): Future[Http.ServerBinding] = {
    Http()
      .newMeteredServerAt(
        interface = interface,
        port = port,
        prepareHttpMetricRegistry(metricsRegistry)
      )
      .enableHttps(httpsContext)
      .bind(route)
  }

  private def prepareHttpMetricRegistry(metricsRegistry: MetricRegistry): HttpMetricsRegistry = {
    val settings: HttpMetricsSettings = DropwizardSettings.default
    new DropwizardRegistry(settings)(metricsRegistry)
  }

}

// HACK!!! This is awful solution, but it's done for a purpose ProcessesResourcesSpec. The spec will be rewritten with
// RestAssured and the hack won't be needed any more. When it's done, we can remove it.
object RouteInterceptor extends Supplier[Route] {

  private val interceptedRoute: AtomicReference[Option[Route]] = new AtomicReference[Option[Route]](None)

  override def get(): Route = interceptedRoute.get() match {
    case Some(value) => value
    case None        => throw new IllegalStateException("Route was not set")
  }

  def set(route: Route): Unit = interceptedRoute.set(Some(route))
}
