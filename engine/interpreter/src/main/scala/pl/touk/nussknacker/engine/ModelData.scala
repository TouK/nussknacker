package pl.touk.nussknacker.engine

import java.net.URL

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.compile.ProcessValidator
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.{ConfigCreatorSignalDispatcher, ProcessDefinitionExtractor}
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

object ModelData {

  def apply(processConfig: Config, classpath: List[URL]) : ModelData = {
    //TODO: ability to generate additional classpath?
    val jarClassLoader = ModelClassLoader(classpath)
    ClassLoaderModelData(processConfig, jarClassLoader)
  }

  //TODO: remove jarPath
  case class ClasspathConfig(jarPath: Option[URL], classpath: Option[List[URL]]) {
    def urls: List[URL] = jarPath.toList ++ classpath.getOrElse(List())
  }

}


case class ClassLoaderModelData(processConfigFromConfiguration: Config, modelClassLoader: ModelClassLoader)
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

  private lazy val processWithObjectsDefinition =
    withThisAsContextClassLoader {
      ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig)
    }

  lazy val processDefinition: ProcessDefinition[ObjectDefinition] = ProcessDefinitionExtractor.toObjectDefinition(processWithObjectsDefinition)

  lazy val validator: ProcessValidator = ProcessValidator.default(processWithObjectsDefinition, modelClassLoader.classLoader)

  lazy val processDefinitionCategories: List[String] = {
    processDefinition.categories
  }

  def withThisAsContextClassLoader[T](block: => T) : T = {
    ThreadUtils.withThisAsContextClassLoader(modelClassLoader.classLoader) {
      block
    }
  }

  def modelClassLoader : ModelClassLoader

  def processConfigFromConfiguration: Config

  override def processConfig: Config = {
    //This allows to add reference.conf to model jar and use properties from there
    //NOTE: substitutions work only one way, i.e. config from NK configuration can use properties from reference.conf, but not vice-versa
    ConfigFactory.load(modelClassLoader.classLoader, processConfigFromConfiguration)
  }
}
