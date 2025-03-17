package pl.touk.nussknacker.ui.server

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.http.scaladsl.server.Route
import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.akka.http.metrics.core.HttpMetrics._
import fr.davit.akka.http.metrics.dropwizard.{DropwizardRegistry, DropwizardSettings}
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.factory.InfrastructureServices
import pl.touk.nussknacker.ui.security.ssl.HttpsConnectionContextFactory

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success}

class NussknackerHttpServer(actorSystem: ActorSystem, metricsRegistry: MetricRegistry, designerConfig: DesignerConfig)
    extends LazyLogging {

  private implicit val systemImplicit: ActorSystem                = actorSystem
  private implicit val executionContextImplicit: ExecutionContext = actorSystem.dispatcher

  def this(infrastructureServices: InfrastructureServices, designerConfig: DesignerConfig) = {
    this(infrastructureServices.actorSystem, infrastructureServices.metricsRegistry, designerConfig)
  }

  def start(route: Route): Resource[IO, Unit] = {
    createAkkaHttpBinding(route).map(_ => RouteInterceptor.set(route))
  }

  private def createAkkaHttpBinding(route: Route) = {
    def createServer() = IO.fromFuture {
      IO {
        val bindingResultF = designerConfig.ssl match {
          case Some(keyStoreConfig) =>
            bindHttps(
              designerConfig.http.interface,
              designerConfig.http.port,
              HttpsConnectionContextFactory.createServerContext(keyStoreConfig),
              route,
              metricsRegistry
            )
          case None =>
            bindHttp(designerConfig.http.interface, designerConfig.http.port, route, metricsRegistry)
        }
        bindingResultF
          .onComplete {
            case Success(bindingResult) =>
              logger.info(
                s"Nussknacker designer started on ${designerConfig.http.interface}:${bindingResult.localAddress.getPort}"
              )
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
