package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.util.{ExecutionContextWithIORuntime, ExecutionContextWithIORuntimeAdapter}
import pl.touk.nussknacker.ui.config.DesignerConfig
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.util.IOToFutureSttpBackendConverter
import sttp.client3.SttpBackend
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

import java.time.Clock
import scala.concurrent.Future

final class InfrastructureServices(
    val clock: Clock,
    val dbRef: DbRef,
    val dbioRunner: DBIOActionRunner,
    val metricsRegistry: MetricRegistry,
    val ioSttpBackend: SttpBackend[IO, Any]
)(
    implicit val executionContextWithIORuntime: ExecutionContextWithIORuntime,
    val actorSystem: ActorSystem
) {

  val futureSttpBackend: SttpBackend[Future, Any] =
    IOToFutureSttpBackendConverter.convert(ioSttpBackend)(executionContextWithIORuntime)

}

object InfrastructureServices {

  def create(clock: Clock, alreadyLoadedConfig: DesignerConfig): Resource[IO, InfrastructureServices] = {
    for {
      actorSystem                   <- createActorSystem(alreadyLoadedConfig)
      executionContextWithIORuntime <- ExecutionContextWithIORuntimeAdapter.createFrom(actorSystem.dispatcher)
      ioSttpBackend                 <- AsyncHttpClientCatsBackend.resource[IO]()

      dbRef           <- DbRef.create(alreadyLoadedConfig.rawConfig)
      metricsRegistry <- createGeneralPurposeMetricsRegistry()
      dbioRunner = DBIOActionRunner(dbRef)(executionContextWithIORuntime)
    } yield new InfrastructureServices(
      clock = clock,
      dbRef = dbRef,
      dbioRunner = dbioRunner,
      metricsRegistry = metricsRegistry,
      ioSttpBackend = ioSttpBackend
    )(
      executionContextWithIORuntime = executionContextWithIORuntime,
      actorSystem = actorSystem
    )
  }

  private def createActorSystem(designerConfig: DesignerConfig) = {
    Resource
      .make(
        acquire = IO(ActorSystem("nussknacker-designer", designerConfig.rawConfig))
      )(
        release = system => {
          IO.fromFuture(IO(system.terminate())).map(_ => ())
        }
      )
  }

  private def createGeneralPurposeMetricsRegistry() = {
    Resource.pure[IO, MetricRegistry](new MetricRegistry)
  }

}
