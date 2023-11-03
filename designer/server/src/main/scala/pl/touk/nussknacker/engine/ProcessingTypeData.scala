package pl.touk.nussknacker.engine

import _root_.sttp.client3.SttpBackend
import akka.actor.ActorSystem
import cats.data.Validated.Valid
import cats.data.ValidatedNel
import cats.implicits.catsSyntaxValidatedId
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition.ToStaticObjectDefinitionTransformer
import pl.touk.nussknacker.ui.statistics.ProcessingTypeUsageStatistics

import scala.concurrent.{ExecutionContext, Future}

final case class ProcessingTypeData private (
    deploymentData: DeploymentData,
    modelData: ModelData,
    categoriesConfig: CategoriesConfig,
    usageStatistics: ProcessingTypeUsageStatistics
) {

  val staticObjectsDefinition: ProcessDefinition[ObjectDefinition] =
    ToStaticObjectDefinitionTransformer.transformModel(
      modelData,
      deploymentData.metaDataInitializer.create(_, Map.empty)
    )

  def close(): Unit = {
    modelData.close()
    deploymentData.deploymentManager.close()
  }

}

case class DeploymentData(
    validDeploymentManager: ValidatedNel[String, DeploymentManager],
    metaDataInitializer: MetaDataInitializer,
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    additionalValidators: List[CustomProcessValidator]
) {

  def deploymentManager: DeploymentManager = validDeploymentManager.valueOr(err =>
    // TODO: Instead of throwing this exception we should follow Null object design pattern and return some
    //       stubbed DeploymentManager which will always return some meaningful status and not allow to run actions on scenario
    throw new IllegalStateException("Deployment Manager not available because of errors: " + err.toList.mkString(", "))
  )

}

// TODO: remove Option after fully switch to categories inside processing types configuration format -
//       see ConfigProcessCategoryService for details
case class CategoriesConfig(categories: Option[List[String]])

object CategoriesConfig {

  def apply(processTypeConfig: ProcessingTypeConfig): CategoriesConfig = new CategoriesConfig(
    processTypeConfig.categories
  )

  def apply(categories: List[String]): CategoriesConfig = new CategoriesConfig(Some(categories))

}

object ProcessingTypeData {

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      processTypeConfig: ProcessingTypeConfig
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    val managerConfig = processTypeConfig.deploymentConfig
    createProcessingTypeData(
      deploymentManagerProvider,
      ModelData(processTypeConfig),
      managerConfig,
      CategoriesConfig(processTypeConfig)
    )
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      modelData: ModelData,
      managerConfig: Config,
      categoriesConfig: CategoriesConfig
  )(
      implicit ec: ExecutionContext,
      actorSystem: ActorSystem,
      sttpBackend: SttpBackend[Future, Any],
      deploymentService: ProcessingTypeDeploymentService
  ): ProcessingTypeData = {
    // TODO: We should catch exceptions and translate them to list of errors. But before that
    //       we should resolve comment next to DeploymentData.deploymentManager
    val validManager = deploymentManagerProvider.createDeploymentManager(modelData, managerConfig).validNel[String]
    createProcessingTypeData(deploymentManagerProvider, validManager, modelData, managerConfig, categoriesConfig)
  }

  def createProcessingTypeData(
      deploymentManagerProvider: DeploymentManagerProvider,
      validManager: ValidatedNel[String, DeploymentManager],
      modelData: ModelData,
      managerConfig: Config,
      categoriesConfig: CategoriesConfig
  ): ProcessingTypeData = {
    import net.ceedubs.ficus.Ficus._
    import pl.touk.nussknacker.engine.util.config.FicusReaders._
    val scenarioProperties =
      deploymentManagerProvider.scenarioPropertiesConfig(managerConfig) ++ modelData.processConfig
        .getOrElse[Map[String, ScenarioPropertyConfig]]("scenarioPropertiesConfig", Map.empty)

    val metaDataInitializer = deploymentManagerProvider.metaDataInitializer(managerConfig)

    ProcessingTypeData(
      DeploymentData(
        validManager,
        metaDataInitializer,
        scenarioProperties,
        deploymentManagerProvider.additionalValidators(managerConfig),
      ),
      modelData,
      categoriesConfig,
      ProcessingTypeUsageStatistics(managerConfig)
    )
  }

}
