package pl.touk.nussknacker.engine

import java.net.URL

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.dict.{DictRegistry, UiDictServices}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.{ConfigCreatorSignalDispatcher, DefinitionExtractor, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

object ModelData extends LazyLogging {

  def apply(processConfig: Config, classpath: List[URL]) : ModelData = {
    //TODO: ability to generate additional classpath?
    val jarClassLoader = ModelClassLoader(classpath)
    logger.debug("Loading model data from classpath: " + classpath)
    ClassLoaderModelData(ModelConfigToLoad(processConfig), jarClassLoader)
  }

  //TODO: remove jarPath
  case class ClasspathConfig(jarPath: Option[URL], classpath: Option[List[URL]]) {
    def urls: List[URL] = jarPath.toList ++ classpath.getOrElse(List())
  }

}


case class ClassLoaderModelData(processConfigFromConfiguration: ModelConfigToLoad, modelClassLoader: ModelClassLoader)
  extends ModelData {

  //this is not lazy, to be able to detect if creator can be created...
  val configCreator : ProcessConfigCreator = ProcessConfigCreatorLoader.justOne(modelClassLoader.classLoader)

  lazy val migrations: ProcessMigrations = {
    Multiplicity(ScalaServiceLoader.load[ProcessMigrations](modelClassLoader.classLoader)) match {
      case Empty() => ProcessMigrations.empty
      case One(migrationsDef) => migrationsDef
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ProcessMigrations instance found: $moreThanOne")
    }
  }


}

trait ModelData extends ConfigCreatorSignalDispatcher {

  def migrations: ProcessMigrations

  def configCreator: ProcessConfigCreator

  lazy val processWithObjectsDefinition: ProcessDefinition[DefinitionExtractor.ObjectWithMethodDef] =
    withThisAsContextClassLoader {
      ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig)
    }

  lazy val processDefinition: ProcessDefinition[ObjectDefinition] = ProcessDefinitionExtractor.toObjectDefinition(processWithObjectsDefinition)

  // We can create dict services here because ModelData is fat object that is created once on start
  lazy val dictServices: UiDictServices =
    DictServicesFactoryLoader.justOne(modelClassLoader.classLoader).createUiDictServices(processDefinition.expressionConfig.dictionaries, processConfig)

  lazy val validator: ProcessValidator = ProcessValidator.default(processWithObjectsDefinition, dictServices.dictRegistry, modelClassLoader.classLoader)

  def withThisAsContextClassLoader[T](block: => T) : T = {
    ThreadUtils.withThisAsContextClassLoader(modelClassLoader.classLoader) {
      block
    }
  }

  def modelClassLoader : ModelClassLoader

  def processConfigFromConfiguration: ModelConfigToLoad

  override lazy val processConfig: Config = processConfigFromConfiguration.loadConfig(modelClassLoader.classLoader)

}
