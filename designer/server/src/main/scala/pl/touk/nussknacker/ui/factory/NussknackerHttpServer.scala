package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.{Http, HttpsConnectionContext}
import akka.stream.Materializer
import cats.effect.{ContextShift, IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import fr.davit.akka.http.metrics.core.HttpMetrics._
import fr.davit.akka.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.akka.http.metrics.dropwizard.{DropwizardRegistry, DropwizardSettings}
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion
import pl.touk.nussknacker.ui.db.DbConfig
import pl.touk.nussknacker.ui.security.ssl.{HttpsConnectionContextFactory, SslConfigParser}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class NussknackerHttpServer(system: ActorSystem,
                            materializer: Materializer,
                            executionContext: ExecutionContext,
                            processingTypeDataProviderFactory: ProcessingTypeDataProviderFactory)
  extends NusskanckerAkkaHttpBasedRouter
    with LazyLogging {

  private implicit val systemImplicit: ActorSystem = system
  private implicit val materializerImplicit: Materializer = materializer
  private implicit val executionContextImplicit: ExecutionContext = executionContext
  private implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)

  def start(config: ConfigWithUnresolvedVersion,
            dbConfig: DbConfig,
            metricRegistry: MetricRegistry): Resource[IO, Unit] = {
    for {
      route <- createRoute(config, dbConfig, metricRegistry, processingTypeDataProviderFactory)
      _ <- createAkkaHttpBinding(config, route, metricRegistry)
    } yield ()
  }

  private def createAkkaHttpBinding(config: ConfigWithUnresolvedVersion,
                                    route: Route,
                                    metricsRegistry: MetricRegistry) = {
    def createServer() = IO.fromFuture {
      IO {
        val interface: String = config.resolved.getString("http.interface")
        val port: Int = config.resolved.getInt("http.port")

        val bindingResultF = SslConfigParser.sslEnabled(config.resolved) match {
          case Some(keyStoreConfig) =>
            bindHttps(interface, port, HttpsConnectionContextFactory.createServerContext(keyStoreConfig), route, metricsRegistry)
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

  private def bindHttp(interface: String,
                       port: Int,
                       route: Route,
                       metricsRegistry: MetricRegistry): Future[Http.ServerBinding] = {
    Http()
      .newMeteredServerAt(
        interface = interface,
        port = port,
        prepareHttpMetricRegistry(metricsRegistry)
      )
      .bind(route)
  }

  private def bindHttps(interface: String,
                        port: Int,
                        httpsContext: HttpsConnectionContext,
                        route: Route,
                        metricsRegistry: MetricRegistry): Future[Http.ServerBinding] = {
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
