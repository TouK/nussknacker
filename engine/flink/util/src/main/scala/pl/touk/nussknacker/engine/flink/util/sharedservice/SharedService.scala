package pl.touk.nussknacker.engine.flink.util.sharedservice

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.MetaData

import scala.reflect.ClassTag

/*
  SharedService API can be used to share expensive resources between tasks/operators. E.g. we don't want to
  create HTTP connection pool for each task, as it's expensive, or rarely used KafkaProducer (e.g for error handling) - as they
  create own threads etc.
  The main problem (except of possible performance problems coming from sharing resources) is: when to close shared resource.
  We use reference counting to know when "last" usage is closed.

  This API should be used with care, used incorrectly it can lead to resource leaks.

  Implementation notes:
  - we
 */
abstract class SharedServiceHolder[CreationData, Service <: SharedService[CreationData] : ClassTag] extends LazyLogging {

  private var sharedServices = Map[CreationData, (Service, Int)]()

  def retrieveService(creationData: CreationData)(implicit metaData: MetaData): Service = synchronized {
    val processId = metaData.id
    val newValue = sharedServices.get(creationData) match {
      case Some((currentInstance, counter)) =>
        logger.debug(s"Retrieving $name from cache for config: $creationData and $processId")
        (currentInstance, counter + 1)
      case None =>
        logger.info(s"Creating new $name for config: $creationData for $processId")
        (createService(creationData, metaData), 1)

    }
    sharedServices += (creationData -> newValue)
    newValue._1
  }

  def returnService(creationData: CreationData): Unit = synchronized {
    sharedServices.get(creationData) match {
      case Some((service, 1)) =>
        logger.debug(s"Closing $name")
        service.internalClose()
        sharedServices -= creationData
        logger.debug(s"Closed $name")
      case Some((service, count)) =>
        logger.debug(s"Attempt to close $name, but it is used by ${count - 1} more objects")
        sharedServices += (creationData -> (service, count - 1))
      case None =>
        throw new IllegalArgumentException(s"Attempt to return $name for $creationData which is not registered!")
    }
  }

  protected def name: String = implicitly[reflect.ClassTag[Service]].runtimeClass.getSimpleName

  protected def createService(config: CreationData, metaData: MetaData): Service

}


trait SharedService[CreationData] extends AutoCloseable {

  final override def close(): Unit = {
    sharedServiceHolder.returnService(creationData)
  }

  def creationData: CreationData

  protected def sharedServiceHolder: SharedServiceHolder[CreationData, _]

  protected[sharedservice] def internalClose(): Unit
}
