package pl.touk.nussknacker.ui.factory

import akka.actor.ActorSystem
import cats.effect.IO
import io.dropwizard.metrics5.MetricRegistry
import pl.touk.nussknacker.engine.util.ExecutionContextWithIORuntime
import pl.touk.nussknacker.ui.db.DbRef
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.util.IOToFutureSttpBackendConverter
import sttp.client3.SttpBackend

import java.time.Clock
import scala.concurrent.Future

final case class InfrastructureServices(
    clock: Clock,
    dbRef: DbRef,
    dbioRunner: DBIOActionRunner,
    metricsRegistry: MetricRegistry,
    ioSttpBackend: SttpBackend[IO, Any]
)(
    implicit val executionContextWithIORuntime: ExecutionContextWithIORuntime,
    val actorSystem: ActorSystem
) {

  val futureSttpBackend: SttpBackend[Future, Any] =
    IOToFutureSttpBackendConverter.convert(ioSttpBackend)(executionContextWithIORuntime)

}
