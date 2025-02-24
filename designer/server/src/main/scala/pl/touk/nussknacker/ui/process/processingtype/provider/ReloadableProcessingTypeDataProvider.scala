package pl.touk.nussknacker.ui.process.processingtype.provider

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.security.api.NussknackerInternalUser

import scala.util.Failure

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
) extends ProcessingTypeDataProvider[Data, CombinedData]
    with LazyLogging {

  // We initiate state with dumb value instead of calling loadMethod() to avoid problems with dependency injection
  // cycle - see NusskanckerDefaultAppRouter.create
  private var stateValue: ProcessingTypeDataState[Data, CombinedData] = emptyState

  override protected[provider] def state: ProcessingTypeDataState[Data, CombinedData] = {
    synchronized {
      stateValue
    }
  }

  def reloadAll(): IO[Unit] = synchronized {
    for {
      beforeReload <- IO.pure(stateValue)
      _ <- IO(
        logger.info(
          s"Closing state with old processing types [${beforeReload.all.keys.toList.sorted
              .mkString(", ")}] and identity [${beforeReload.stateIdentity}]"
        )
      )
      _ <- close(beforeReload)
      _ <- IO(
        logger.info("Reloading processing type data...")
      )
      newState <- loadMethod
      _ <- IO(
        logger.info(
          s"New state with processing types [${state.all.keys.toList.sorted.mkString(", ")}] and identity [${state.stateIdentity}] reloaded finished"
        )
      )
    } yield {
      stateValue = newState
    }
  }

  def close(): IO[Unit] = synchronized {
    for {
      _ <- IO {
        stateValue = emptyState
      }
      _ <- close(stateValue)
    } yield ()
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

  private def emptyState = new ProcessingTypeDataState(
    all = Map.empty,
    combinedDataTry = Failure(new IllegalAccessException("ProcessingTypeData is not initialized")),
    stateIdentity = new Object
  )

}
