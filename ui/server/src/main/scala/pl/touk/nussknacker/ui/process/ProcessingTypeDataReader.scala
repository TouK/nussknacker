package pl.touk.nussknacker.ui.process

import java.net.URL
import java.util.ServiceLoader

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.{ProcessManagerProvider, ProcessingTypeConfig, ProcessingTypeData}

object ProcessingTypeDataReader extends LazyLogging {

  import scala.collection.JavaConverters._

  def readProcessingTypeData(config: Config): Map[String, ProcessingTypeData] = {

    val providers = ServiceLoader.load(classOf[ProcessManagerProvider])
      .asScala.toList.map(p => p.name -> p).toMap

    implicit val reader: ValueReader[Map[String, ProcessingTypeConfig]] = ValueReader.relative { config =>
      config.root().entrySet().asScala.map(_.getKey).map { key =>
        key -> config.as[ProcessingTypeConfig](key)(ProcessingTypeConfig.reader)
      }.toMap
    }

    val types = config.as[Map[String, ProcessingTypeConfig]]("processTypes")
    types.map {
      case (name, typeConfig) =>
        logger.debug(s"Creating process manager: $name with config: $typeConfig")
        val managerProvider = providers.getOrElse(typeConfig.engineType,
          throw new IllegalArgumentException(s"Cannot find manager type: $name, available names: ${providers.keys}"))
        name -> ProcessingTypeData.createProcessingTypeData(managerProvider, typeConfig)
    }
  }



}
