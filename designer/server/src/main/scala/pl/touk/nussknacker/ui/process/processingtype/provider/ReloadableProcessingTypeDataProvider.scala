package pl.touk.nussknacker.ui.process.processingtype.provider

import cats.effect.IO
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
class ReloadableProcessingTypeDataProvider[Data <: AutoCloseable, CombinedData](
    loadMethod: IO[ProcessingTypeDataState[Data, CombinedData]]
) extends ProcessingTypeDataProvider[Data, CombinedData](ProcessingTypeDataState.uninitialized)
    with LazyLogging {

  def reloadAll(): IO[Unit] = {
    stateMutex.lock.surround {
      for {
        beforeReload <- stateRef.get
        _ <- IO(
          logger.info(
            s"Closing state with old processing types [${beforeReload.all.keys.toList.sorted.mkString(", ")}]"
          )
        )
        _ <- close(beforeReload)
        _ <- IO(
          logger.info("Reloading processing type data...")
        )
        newState <- loadMethod
        _ <- setStateValueAndNotifyObservers(newState)
          .map { _ =>
            logger.info(
              s"New state with processing types [${newState.all.keys.toList.sorted.mkString(", ")}] reload finished"
            )
          }
          .recoverWith { case NonFatal(ex) =>
            logger.error("Error occurred during reloading state. Rolling back previous state value", ex)
            setStateValueAndNotifyObservers(beforeReload).map { _ =>
              throw ex
            }
          }
      } yield ()
    }
  }

  def close(): IO[Unit] = {
    stateMutex.lock.surround {
      for {
        beforeSetToEmpty <- stateRef.get
        // It is better to return empty state than closed state
        _ <- stateRef.set(ProcessingTypeDataState.uninitialized)
        _ <- close(beforeSetToEmpty)
      } yield ()
    }
  }

  private def close(state: ProcessingTypeDataState[Data, CombinedData]) = IO {
    state.all.values.foreach(
      _.valueWithAllowedAccess(Permission.Read)(NussknackerInternalUser.instance)
        .getOrElse(
          throw new IllegalStateException("NussknackerInternalUser without read access to ProcessingTypeData")
        )
        .close()
    )
  }

}
