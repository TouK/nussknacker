package pl.touk.nussknacker.ui.process.processingtypedata

import java.util.ServiceLoader

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{ProcessManagerProvider, ProcessingTypeConfig, ProcessingTypeData}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ValueReader

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

