package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.dict.{DictServicesFactory, EngineDictRegistry, UiDictServices}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.{
  ModelDefinition,
  ModelDefinitionExtractor,
  ModelDefinitionWithClasses
}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig.{DefaultModelConfigLoader, InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider

import java.net.URL

object ModelData extends LazyLogging {

  def apply(processingTypeConfig: ProcessingTypeConfig): ModelData = {
    ModelData(
      processingTypeConfig.modelConfig,
      ModelClassLoader(processingTypeConfig.classPath),
      processingTypeConfig.category
    )
  }

  def apply(inputConfig: Config, modelClassLoader: ModelClassLoader, category: Option[String]): ModelData = {
    ModelData(ConfigWithUnresolvedVersion(modelClassLoader.classLoader, inputConfig), modelClassLoader, category)
  }

  def apply(
      inputConfig: ConfigWithUnresolvedVersion,
      modelClassLoader: ModelClassLoader,
      category: Option[String]
  ): ModelData = {
    logger.debug("Loading model data from: " + modelClassLoader)
    ClassLoaderModelData(
      modelConfigLoader =>
        modelConfigLoader.resolveInputConfigDuringExecution(inputConfig, modelClassLoader.classLoader),
      modelClassLoader,
      category
    )
  }

  // Used on Flink, where we start already with resolved config so we should not resolve it twice.
  def duringExecution(inputConfig: Config): ModelData = {
    ClassLoaderModelData(_ => InputConfigDuringExecution(inputConfig), ModelClassLoader(Nil), None)
  }

  implicit class BaseModelDataExt(baseModelData: BaseModelData) {
    def asInvokableModelData: ModelData = baseModelData.asInstanceOf[ModelData]
  }

}

case class ClassLoaderModelData private (
    private val resolveInputConfigDuringExecution: ModelConfigLoader => InputConfigDuringExecution,
    modelClassLoader: ModelClassLoader,
    override val category: Option[String]
) extends ModelData {

  // this is not lazy, to be able to detect if creator can be created...
  override val configCreator: ProcessConfigCreator = ProcessConfigCreatorLoader.justOne(modelClassLoader.classLoader)

  override lazy val modelConfigLoader: ModelConfigLoader = {
    Multiplicity(ScalaServiceLoader.load[ModelConfigLoader](modelClassLoader.classLoader)) match {
      case Empty()                => new DefaultModelConfigLoader
      case One(modelConfigLoader) => modelConfigLoader
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ModelConfigLoader instance found: $moreThanOne")
    }
  }

  override lazy val inputConfigDuringExecution: InputConfigDuringExecution = resolveInputConfigDuringExecution(
    modelConfigLoader
  )

  override lazy val migrations: ProcessMigrations = {
    Multiplicity(ScalaServiceLoader.load[ProcessMigrations](modelClassLoader.classLoader)) match {
      case Empty()            => ProcessMigrations.empty
      case One(migrationsDef) => migrationsDef
      case Many(moreThanOne) =>
        throw new IllegalArgumentException(s"More than one ProcessMigrations instance found: $moreThanOne")
    }
  }

  override def objectNaming: ObjectNaming = ObjectNamingProvider(modelClassLoader.classLoader)
}

trait ModelData extends BaseModelData with AutoCloseable {

  def migrations: ProcessMigrations

  def configCreator: ProcessConfigCreator

  // It won't be necessary after we get rid of ProcessConfigCreator API
  def category: Option[String]

  lazy val modelDefinitionWithClasses: ModelDefinitionWithClasses = {
    val modelDefinitions = withThisAsContextClassLoader {
      ModelDefinitionExtractor.extractModelDefinition(
        configCreator,
        modelClassLoader.classLoader,
        ProcessObjectDependencies(modelConfig, objectNaming),
        category
      )
    }
    ModelDefinitionWithClasses(modelDefinitions)
  }

  final def modelDefinition: ModelDefinition[ComponentDefinitionWithImplementation] =
    modelDefinitionWithClasses.modelDefinition

  private lazy val dictServicesFactory: DictServicesFactory =
    DictServicesFactoryLoader.justOne(modelClassLoader.classLoader)

  lazy val designerDictServices: UiDictServices =
    dictServicesFactory.createUiDictServices(modelDefinition.expressionConfig.dictionaries, modelConfig)

  lazy val engineDictRegistry: EngineDictRegistry =
    dictServicesFactory.createEngineDictRegistry(modelDefinition.expressionConfig.dictionaries)

  def customProcessValidator: CustomProcessValidator = {
    CustomProcessValidatorLoader.loadProcessValidators(modelClassLoader.classLoader, modelConfig)
  }

  def withThisAsContextClassLoader[T](block: => T): T = {
    ThreadUtils.withThisAsContextClassLoader(modelClassLoader.classLoader) {
      block
    }
  }

  override def modelClassLoaderUrls: List[URL] = modelClassLoader.urls

  def modelClassLoader: ModelClassLoader

  def modelConfigLoader: ModelConfigLoader

  override lazy val modelConfig: Config =
    modelConfigLoader.resolveConfig(inputConfigDuringExecution, modelClassLoader.classLoader)

  def close(): Unit = {
    designerDictServices.close()
  }

}
