package pl.touk.nussknacker.ui.process.processingtype.provider

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

import scala.util.control.NonFatal

/**
 * This implements *simplistic* reloading of ProcessingTypeData - treat it as experimental/working PoC
 *
 * One of the biggest issues is that it can break current operations - when reloadAll is invoked, e.g. during
 * process deploy via FlinkRestManager it may very well happen that http backed is closed between two Flink invocations.
 * To handle this correctly we probably need sth like:
 * def withProcessingTypeData(processingType: ProcessingType)(action: ProcessingTypeData=>Future[T]): Future[T]
 * to be able to wait for all operations to complete
 *
 * Another thing that needs careful consideration is handling exception during ProcessingTypeData creation/closing - probably during
 * close we want to catch exception and try to proceed, but during creation it can be a bit tricky...
 */
class ReloadableProcessingTypeDataProvider[Data <: AutoCloseable, CombinedData] private (
    loadMethod: IO[ProcessingTypeDataState[Data, CombinedData]]
)(implicit ioRuntime: IORuntime)
    extends ProcessingTypeDataProvider[Data, CombinedData](loadMethod.unsafeRunSync())
    with LazyLogging {

  def reloadAll: IO[Unit] = {
    accessStateInCriticalSection { stateOps =>
      for {
        beforeReload <- stateOps.value
        _ <- IO(
          logger.info(
            s"Closing state with old processing types [${beforeReload.all.keys.toList.sorted.mkString(", ")}]"
          )
        )
        _ <- closeState(beforeReload)
        _ <- IO(
          logger.info("Reloading processing type data...")
        )
        newState <- loadMethod
        _ <- stateOps
          .setValue(newState)
          .map { _ =>
            logger.info(
              s"New state with processing types [${newState.all.keys.toList.sorted.mkString(", ")}] reload finished"
            )
          }
          .recoverWith { case NonFatal(ex) =>
            logger.error("Error occurred during reloading state. Rolling back previous state value", ex)
            stateOps.setValue(beforeReload).map { _ =>
              throw ex
            }
          }
      } yield ()
    }
  }

  // We have to shut down IORuntime in one place, after closing all observers to ensure that nobody will use closed IORuntime
  override protected def finalizeAfterClosingObserversAndState: IO[Unit] = {
    IO(ioRuntime.shutdown())
  }

  override protected def closeState(state: ProcessingTypeDataState[Data, CombinedData]): IO[Unit] = IO {
    state.all.values.foreach(
      _.valueWithAllowedAccess(Permission.Read)(NussknackerInternalUser.instance)
        .getOrElse(
          throw new IllegalStateException("NussknackerInternalUser without read access to ProcessingTypeData")
        )
        .close()
    )
  }

}

object ReloadableProcessingTypeDataProvider {

  def apply[Data <: AutoCloseable, CombinedData](
      loadMethod: IO[ProcessingTypeDataState[Data, CombinedData]]
  ): ReloadableProcessingTypeDataProvider[Data, CombinedData] = {
    // We create separate ioRuntime to ensure that we don't have some deadlocks during reading state where is used unsafeRunSync()
    // See ProcessingTypeDataProvider.state method
    implicit val ioRuntime: IORuntime = IORuntime.builder().build()
    new ReloadableProcessingTypeDataProvider[Data, CombinedData](loadMethod)
  }

}
