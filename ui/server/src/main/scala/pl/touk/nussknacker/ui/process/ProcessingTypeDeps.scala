package pl.touk.nussknacker.ui.process

import java.net.URL
import java.util.ServiceLoader

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.api.deployment.ProcessManager
import pl.touk.nussknacker.engine.management.FlinkStreamingProcessManagerProvider
import pl.touk.nussknacker.engine.standalone.management.StandaloneProcessManagerProvider
import pl.touk.nussknacker.engine.{ModelData, ProcessManagerProvider, ProcessingTypeConfig, ProcessingTypeData}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType

case class ProcessingTypeDeps(managers: Map[ProcessingType, ProcessManager],
                              modelData: Map[ProcessingType, ModelData])

object ProcessingTypeDeps extends LazyLogging {

  import pl.touk.nussknacker.engine.util.config.FicusReaders._
  import scala.collection.JavaConverters._

  implicit val reader: ValueReader[Map[String, ProcessingTypeConfig]] = ValueReader.relative { config =>
    config.root().entrySet().asScala.map(_.getKey).toList.map { key =>
      val value = config.getConfig(key)
      key -> ProcessingTypeConfig(
        value.getString("engineConfig.type"),
        value.as[List[URL]]("modelConfig.classPath"),
        value.getConfig("engineConfig"),
        value.getConfig("modelConfig")
      )
    }.toMap
  }

  def apply(config: Config, standaloneModeEnabled: Boolean): Map[String, ProcessingTypeData] = {

    val types = if (config.hasPath("processTypes")) {
      config.as[Map[String, ProcessingTypeConfig]]("processTypes")
    } else {
      //TODO: this is legacy mode, should be removed in the future...
      val str = Map("streaming" -> FlinkStreamingProcessManagerProvider.defaultTypeConfig(config))
      if (standaloneModeEnabled) {
        str + ("request-response" -> StandaloneProcessManagerProvider.defaultTypeConfig(config))
      } else str
    }
    createProcessManagers(types)
  }

  import scala.collection.JavaConverters._


  def createProcessManagers(configuredTypes: Map[String, ProcessingTypeConfig]): Map[String, ProcessingTypeData] = {
    val providers = ServiceLoader.load(classOf[ProcessManagerProvider])
      .asScala.toList.map(p => p.name -> p).toMap

    configuredTypes.map {
      case (name, typeConfig) =>
        logger.debug(s"Creating process manager: $name with config: $typeConfig")
        val managerProvider = providers.getOrElse(typeConfig.engineType,
          throw new IllegalArgumentException(s"Cannot find manager type: $name, available names: ${providers.keys}"))
        name -> ProcessingTypeData.createProcessManager(managerProvider, typeConfig)
    }
  }



}
