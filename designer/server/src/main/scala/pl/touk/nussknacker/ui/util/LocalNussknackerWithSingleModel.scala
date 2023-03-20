package pl.touk.nussknacker.ui.util

import akka.actor.ActorSystem
import com.typesafe.config.ConfigValueFactory._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.api.deployment.{DeployedScenarioData, ProcessingTypeDeploymentService}
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{BasicProcessingTypeDataReload, DefaultProcessingTypeDeploymentService, Initialization, MapBasedProcessingTypeDataProvider, ProcessingTypeDataProvider, ProcessingTypeDataReload}
import pl.touk.nussknacker.ui.{NusskanckerDefaultAppRouter, NussknackerAppInitializer}
import sttp.client.{NothingT, SttpBackend}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}

//This is helper, which allows for starting UI with given model without having to build jar etc.
// See pl.touk.nussknacker.defaultmodel.RunFlinkStreamingModelLocally for sample usage
object LocalNussknackerWithSingleModel  {

  //default name in config
  val typeName = "streaming"

  def run(modelData: ModelData,
          deploymentManagerProvider: DeploymentManagerProvider,
          managerConfig: Config, categories: Set[String]): Unit = {
    val router = new NusskanckerDefaultAppRouter {
      override protected def prepareProcessingTypeData(config: Config,
                                                       getDeploymentService: () => DeploymentService,
                                                       categoriesService: ProcessCategoryService)
                                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                                       sttpBackend: SttpBackend[Future, Nothing, NothingT]): (ProcessingTypeDataProvider[ProcessingTypeData], ProcessingTypeDataReload with Initialization) = {
        //TODO: figure out how to perform e.g. hotswap
        BasicProcessingTypeDataReload.wrapWithReloader(() => {
          val deploymentService: DeploymentService = getDeploymentService()
          implicit val processTypeDeploymentService: ProcessingTypeDeploymentService = new DefaultProcessingTypeDeploymentService(typeName, deploymentService)
          val data = ProcessingTypeData.createProcessingTypeData(deploymentManagerProvider, modelData, managerConfig)
          new MapBasedProcessingTypeDataProvider(Map(typeName -> data))
        })
      }
    }
    val file: File = prepareUsersFile()
    val configToUse = ConfigFactory.parseMap(Map[String, Any](
      "authentication.usersFile" -> file.getAbsolutePath,
      "categoriesConfig" -> fromMap(categories.map(cat => cat -> typeName).toMap.asJava)
    ).asJava)
    new NussknackerAppInitializer(configToUse).init(router)
  }

  //TODO: easier way of handling users file
  private def prepareUsersFile(): File = {
    val file = Files.createTempFile("users", "conf").toFile
    FileUtils.write(file,
      """users: [
        |  {
        |    identity: "admin"
        |    password: "admin"
        |    roles: ["Admin"]
        |  }
        |]
        |rules: [
        |  {
        |    role: "Admin"
        |    isAdmin: true
        |  }
        |]
        |""".stripMargin, StandardCharsets.UTF_8)
    file.deleteOnExit()
    file
  }
}
