package pl.touk.nussknacker.ui.process

import java.util.ServiceLoader

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.{ProcessManagerProvider, ProcessingTypeConfig, ProcessingTypeData}

/*
  Currently, the only implementation is map-based, but in the future it will allow to reload ProcessingTypeData related stuff
  without restarting the app
  TODO: ensure by types (e.g. appropriate monad?? ;)) that all the methods except mapValues are invoked only in context of single request (or initialization)
 */
trait ProcessingTypeDataProvider[+T] {

  def forType(typ: ProcessingType): Option[T]

  //TODO: replace with proper forType handling
  def forTypeUnsafe(typ: ProcessingType): T = forType(typ).get

  def all: Map[ProcessingType, T]

  def mapValues[Y](fun: T => Y): ProcessingTypeDataProvider[Y] = {

    new ProcessingTypeDataProvider[Y] {

      override def forType(typ: ProcessingType): Option[Y] = ProcessingTypeDataProvider.this.forType(typ).map(fun)

      override def all: Map[ProcessingType, Y] = ProcessingTypeDataProvider.this.all.mapValues(fun)
    }

  }

}

object ProcessingTypeDataReader extends LazyLogging {

  import scala.collection.JavaConverters._

  def readProcessingTypeData(config: Config): ProcessingTypeDataProvider[ProcessingTypeData] = {

    val providers = ServiceLoader.load(classOf[ProcessManagerProvider])
      .asScala.toList.map(p => p.name -> p).toMap

    implicit val reader: ValueReader[Map[String, ProcessingTypeConfig]] = ValueReader.relative { config =>
      config.root().entrySet().asScala.map(_.getKey).map { key =>
        key -> config.as[ProcessingTypeConfig](key)(ProcessingTypeConfig.reader)
      }.toMap
    }

    val types = config.as[Map[String, ProcessingTypeConfig]]("processTypes")
    val valueMap = types.map {
      case (name, typeConfig) =>
        logger.debug(s"Creating process manager: $name with config: $typeConfig")
        val managerProvider = providers.getOrElse(typeConfig.engineType,
          throw new IllegalArgumentException(s"Cannot find manager type: $name, available names: ${providers.keys}"))
        name -> ProcessingTypeData.createProcessingTypeData(managerProvider, typeConfig)
    }
    new MapBasedProcessingTypeDataProvider[ProcessingTypeData](valueMap)
  }




}

class MapBasedProcessingTypeDataProvider[T](map: Map[ProcessingType, T]) extends ProcessingTypeDataProvider[T] {

  override def forType(typ: ProcessingType): Option[T] = map.get(typ)

  override def all: Map[ProcessingType, T] = map

}

