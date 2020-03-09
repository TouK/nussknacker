package pl.touk.nussknacker.ui.process.processingtypedata
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

trait ProcessingTypeDataReload {

  def reloadAll(): Unit

}

/**
 * This implements *simplistic* reloading of ProcessingTypeData - treat it as experimental/working PoC
 * One of the biggest issues is that it can break current operations - when reloadAll is invoked, e.g. during
 * process deploy via FlinkRestManager it may very well happen that http backed is closed between two Flink invocations.
 *
 * To handle this correctly we probably need sth like:
 *
 * def withProcessingTypeData(processingType: ProcessingType)(action: ProcessingTypeData=>Future[T]): Future[T]
 *
 * to be able to wait for all operations to complete
 */
class ReloadableProcessingTypeDataProvider(loadMethod: () => ProcessingTypeDataProvider[ProcessingTypeData])
  extends ProcessingTypeDataProvider[ProcessingTypeData] with ProcessingTypeDataReload {

  @volatile var current: ProcessingTypeDataProvider[ProcessingTypeData] = loadMethod()
  
  override def forType(typ: ProcessingType): Option[ProcessingTypeData] = current.forType(typ)

  override def all: Map[ProcessingType, ProcessingTypeData] = current.all

  override def reloadAll(): Unit = synchronized {
    val old = current
    current = loadMethod()
    old.all.values.foreach(_.close())
  }
}



