package pl.touk.nussknacker.engine

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ClassLoaderModelData.ExtractDefinitionFunImpl
import pl.touk.nussknacker.engine.ModelData.ExtractDefinitionFun
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  ComponentId,
  ComponentProvider,
  DesignerWideComponentId
}
import pl.touk.nussknacker.engine.api.dict.{DictServicesFactory, EngineDictRegistry, UiDictServices}
import pl.touk.nussknacker.engine.api.namespaces.NamingStrategy
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.model.{
  ModelDefinition,
  ModelDefinitionExtractor,
  ModelDefinitionWithClasses
}
import pl.touk.nussknacker.engine.dict.DictServicesFactoryLoader
import pl.touk.nussknacker.engine.migration.ProcessMigrations
import pl.touk.nussknacker.engine.modelconfig._
import pl.touk.nussknacker.engine.util.ThreadUtils
import pl.touk.nussknacker.engine.util.loader.{ModelClassLoader, ProcessConfigCreatorLoader, ScalaServiceLoader}
import pl.touk.nussknacker.engine.util.multiplicity.{Empty, Many, Multiplicity, One}

import java.net.URL
import java.nio.file.Path

object ModelData extends LazyLogging {

  type ExtractDefinitionFun =
    (
        ClassLoader,
        ProcessObjectDependencies,
        ComponentId => DesignerWideComponentId,
        Map[DesignerWideComponentId, ComponentAdditionalConfig]
    ) => ModelDefinition

  def apply(processingTypeConfig: ProcessingTypeConfig, dependencies: ModelDependencies): ModelData = {
    val modelClassLoader = ModelClassLoader(processingTypeConfig.classPath, dependencies.workingDirectoryOpt)
    ClassLoaderModelData(
      modelConfigLoader =>
        modelConfigLoader
          .resolveInputConfigDuringExecution(processingTypeConfig.modelConfig, modelClassLoader.classLoader),
      modelClassLoader,
      Some(processingTypeConfig.category),
      dependencies.determineDesignerWideId,
      dependencies.additionalConfigsFromProvider,
      dependencies.shouldIncludeComponentProvider
    )
  }

  // Used on Flink, where we start already with resolved config so we should not resolve it twice.
  // Also a classloader is correct so we don't need to build the new one
  // This tiny method is Flink specific so probably the interpreter module is not the best one
  // but it is very convenient to keep in near normal, duringExecution method
  def duringFlinkExecution(inputConfig: Config): ModelData = {
    duringExecution(inputConfig, ModelClassLoader.empty, resolveConfigs = false)
  }

  // On the runtime side, we get only model config, not the whole processing type config,
  // so we don't have category, processingType and additionalConfigsFromProvider
  // But it is not a big deal, because scenario was already validated before deploy, so we already check that
  // we don't use not allowed components for a given category
  // and that the scenario doesn't violate validators introduced by additionalConfigsFromProvider
  def duringExecution(inputConfig: Config, modelClassLoader: ModelClassLoader, resolveConfigs: Boolean): ModelData = {
    def resolveInputConfigDuringExecution(modelConfigLoader: ModelConfigLoader): InputConfigDuringExecution = {
      if (resolveConfigs) {
        modelConfigLoader.resolveInputConfigDuringExecution(
          ConfigWithUnresolvedVersion(modelClassLoader.classLoader, inputConfig),
          modelClassLoader.classLoader
        )
      } else {
        InputConfigDuringExecution(inputConfig)
      }
    }
    ClassLoaderModelData(
      resolveInputConfigDuringExecution = resolveInputConfigDuringExecution,
      modelClassLoader = modelClassLoader,
      category = None,
      determineDesignerWideId = id => DesignerWideComponentId(id.toString),
      additionalConfigsFromProvider = Map.empty,
      shouldIncludeComponentProvider = _ => true
    )
  }

  implicit class BaseModelDataExt(baseModelData: BaseModelData) {
    def asInvokableModelData: ModelData = baseModelData.asInstanceOf[ModelData]
  }

}

case class ModelDependencies(
    additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
    determineDesignerWideId: ComponentId => DesignerWideComponentId,
    workingDirectoryOpt: Option[Path],
    // This property is for easier testing when for some reason, some jars with ComponentProvider are
    // on the test classpath and CPs collide with other once with the same name.
    // E.g. we add liteEmbeddedDeploymentManager as a designer provided dependency which also
    // add liteKafkaComponents (which are in test scope), see comment next to designer module
    shouldIncludeComponentProvider: ComponentProvider => Boolean
)

case class ClassLoaderModelData private (
    private val resolveInputConfigDuringExecution: ModelConfigLoader => InputConfigDuringExecution,
    modelClassLoader: ModelClassLoader,
    override val category: Option[String],
    override val determineDesignerWideId: ComponentId => DesignerWideComponentId,
    override val additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig],
    shouldIncludeComponentProvider: ComponentProvider => Boolean
) extends ModelData
    with LazyLogging {

  logger.debug("Loading model data from: " + modelClassLoader)

  // this is not lazy, to be able to detect if creator can be created...
  override val configCreator: ProcessConfigCreator = ProcessConfigCreatorLoader.justOne(modelClassLoader.classLoader)

  override lazy val modelConfigLoader: ModelConfigLoader = {
    Multiplicity(ScalaServiceLoader.load[ModelConfigLoader](modelClassLoader.classLoader)) match {
      case Empty()                => new DefaultModelConfigLoader(shouldIncludeComponentProvider)
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

  override val namingStrategy: NamingStrategy = NamingStrategy.fromConfig(modelConfig)

  override val extractModelDefinitionFun: ExtractDefinitionFun =
    new ExtractDefinitionFunImpl(configCreator, category, shouldIncludeComponentProvider)

}

object ClassLoaderModelData {

  class ExtractDefinitionFunImpl(
      configCreator: ProcessConfigCreator,
      category: Option[String],
      shouldIncludeComponentProvider: ComponentProvider => Boolean
  ) extends ExtractDefinitionFun
      with Serializable {

    override def apply(
        classLoader: ClassLoader,
        modelDependencies: ProcessObjectDependencies,
        determineDesignerWideId: ComponentId => DesignerWideComponentId,
        additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]
    ): ModelDefinition = {
      ModelDefinitionExtractor.extractModelDefinition(
        configCreator,
        classLoader,
        modelDependencies,
        category,
        determineDesignerWideId,
        additionalConfigsFromProvider,
        shouldIncludeComponentProvider
      )
    }

  }

}

trait ModelData extends BaseModelData with AutoCloseable {

  // TODO: We should move model extensions that are used only on designer side into a separate wrapping class e.g. DesignerModelData
  //       Thanks to that we 1) avoid unnecessary noice on runtime side, but also 2) we still see what kind of model extensions
  //       do we have. See AdditionalInfoProviders as well
  def migrations: ProcessMigrations

  final def buildInfo: Map[String, String] = configCreator.buildInfo()

  def configCreator: ProcessConfigCreator

  // It won't be necessary after we get rid of ProcessConfigCreator API
  def category: Option[String]

  def determineDesignerWideId: ComponentId => DesignerWideComponentId

  def additionalConfigsFromProvider: Map[DesignerWideComponentId, ComponentAdditionalConfig]

  final lazy val modelDefinitionWithClasses: ModelDefinitionWithClasses = {
    val modelDefinitions = withThisAsContextClassLoader {
      extractModelDefinitionFun(
        modelClassLoader.classLoader,
        ProcessObjectDependencies(modelConfig, namingStrategy),
        determineDesignerWideId,
        additionalConfigsFromProvider
      )
    }
    ModelDefinitionWithClasses(modelDefinitions)
  }

  // This has to be a function instead of method to explicitly define what scope will be serializable by Flink.
  // See parameters of implementing functions
  def extractModelDefinitionFun: ExtractDefinitionFun

  final def modelDefinition: ModelDefinition =
    modelDefinitionWithClasses.modelDefinition

  private lazy val dictServicesFactory: DictServicesFactory =
    DictServicesFactoryLoader.justOne(modelClassLoader.classLoader)

  final lazy val designerDictServices: UiDictServices =
    dictServicesFactory.createUiDictServices(modelDefinition.expressionConfig.dictionaries, modelConfig)

  final lazy val engineDictRegistry: EngineDictRegistry =
    dictServicesFactory.createEngineDictRegistry(modelDefinition.expressionConfig.dictionaries)

  // TODO: remove it, see notice in CustomProcessValidatorFactory
  final def customProcessValidator: CustomProcessValidator = {
    CustomProcessValidatorLoader.loadProcessValidators(modelClassLoader.classLoader, modelConfig)
  }

  final def withThisAsContextClassLoader[T](block: => T): T = {
    ThreadUtils.withThisAsContextClassLoader(modelClassLoader.classLoader) {
      block
    }
  }

  final override def modelClassLoaderUrls: List[URL] = modelClassLoader.urls

  def modelClassLoader: ModelClassLoader

  def modelConfigLoader: ModelConfigLoader

  final override lazy val modelConfig: Config =
    modelConfigLoader.resolveConfig(inputConfigDuringExecution, modelClassLoader.classLoader)

  final lazy val componentsUiConfig: ComponentsUiConfig = ComponentsUiConfigParser.parse(modelConfig)

  final def close(): Unit = {
    designerDictServices.close()
  }

}
