package pl.touk.nussknacker.ui.process.processingtypedata

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{CombinedProcessingTypeData, ConfigWithUnresolvedVersion, ProcessingTypeData}

/**
 * This implements *simplistic* reloading of ProcessingTypeData - treat it as experimental/working PoC
 *
 * One of the biggest issues is that it can break current operations - when reloadAll is invoked, e.g. during
 * process deploy via FlinkRestManager it may very well happen that http backed is closed between two Flink invocations.
 * To handle this correctly we probably need sth like:
 *   def withProcessingTypeData(processingType: ProcessingType)(action: ProcessingTypeData=>Future[T]): Future[T]
 * to be able to wait for all operations to complete
 *
 * Another thing that needs careful consideration is handling exception during ProcessingTypeData creation/closing - probably during
 * close we want to catch exception and try to proceed, but during creation it can be a bit tricky...
 */
class ProcessingTypeDataReload(
    loadMethod: () => ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData]
) extends ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData]
    with LazyLogging {

  @volatile private[processingtypedata] var state
      : ProcessingTypeDataState[ProcessingTypeData, CombinedProcessingTypeData] =
    // We init state with dumb value instead of calling loadMethod() to avoid problems with dependency injection cycle - see NusskanckerDefaultAppRouter.create
    ProcessingTypeDataState(
      Map.empty,
      () => CombinedProcessingTypeData.create(Map.empty, ConfigWithUnresolvedVersion(ConfigFactory.empty())),
      new Object
    )

  def reloadAll(): Unit = synchronized {
    val beforeReload = state
    logger.info(
      s"Closing state with old processing types [${beforeReload.all.keys.toList.sorted.mkString(", ")}] and identity [${beforeReload.stateIdentity}]"
    )
    beforeReload.all.values.foreach(_.value.close())
    logger.info("Reloading processing type data...")
    state = loadMethod()
    logger.info(
      s"New state with processing types [${state.all.keys.toList.sorted.mkString(", ")}] and identity [${state.stateIdentity}] reloaded finished"
    )
  }

}
