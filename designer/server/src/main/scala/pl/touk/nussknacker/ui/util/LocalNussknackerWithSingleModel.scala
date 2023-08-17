package pl.touk.nussknacker.ui.util

import akka.actor.ActorSystem
import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigValueFactory._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.api.deployment.ProcessingTypeDeploymentService
import pl.touk.nussknacker.engine.{CombinedProcessingTypeData, ConfigWithUnresolvedVersion, DeploymentManagerProvider, ModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.{NusskanckerDefaultAppRouter, NussknackerAppInitializer}
import pl.touk.nussknacker.ui.factory.{LocalProcessingTypeDataProviderFactory, NussknackerApp}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.processingtypedata.{BasicProcessingTypeDataReload, DefaultProcessingTypeDeploymentService, Initialization, MapBasedProcessingTypeDataProvider, ProcessingTypeDataProvider, ProcessingTypeDataReload}
import sttp.client3.SttpBackend

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

//This is helper, which allows for starting UI with given model without having to build jar etc.
// See pl.touk.nussknacker.defaultmodel.RunFlinkStreamingModelLocally for sample usage
object LocalNussknackerWithSingleModel {

  //default name in config
  val typeName = "streaming"

  def run(modelData: ModelData,
          deploymentManagerProvider: DeploymentManagerProvider,
          managerConfig: Config,
          categories: Set[String]): Resource[IO, Unit] = {
    for {
      appConfig <- Resource.eval(IO {
        val file: File = prepareUsersFile()
        ConfigFactory
          .parseMap(
            Map[String, Any](
              "authentication.usersFile" -> file.getAbsoluteFile.toURI.toString,
              "categoriesConfig" -> fromMap(categories.map(cat => cat -> typeName).toMap.asJava)
            ).asJava
          )
      })
      _ <- run2(modelData, deploymentManagerProvider, managerConfig, appConfig)
    } yield ()
  }

  def run2(modelData: ModelData,
          deploymentManagerProvider: DeploymentManagerProvider,
          managerConfig: Config,
          appConfig: Config): Resource[IO, Unit] = Resource.eval[IO, Unit] {
    IO {
      val router = new NusskanckerDefaultAppRouter {
        override protected def prepareProcessingTypeData(config: ConfigWithUnresolvedVersion,
                                                         getDeploymentService: () => DeploymentService,
                                                         categoryService: ProcessCategoryService)
                                                        (implicit ec: ExecutionContext, actorSystem: ActorSystem,
                                                         sttpBackend: SttpBackend[Future, Any]): (ProcessingTypeDataProvider[ProcessingTypeData, CombinedProcessingTypeData], ProcessingTypeDataReload with Initialization) = {
          //TODO: figure out how to perform e.g. hotswap
          BasicProcessingTypeDataReload.wrapWithReloader(() => {
            val deploymentService: DeploymentService = getDeploymentService()
            implicit val processTypeDeploymentService: ProcessingTypeDeploymentService = new DefaultProcessingTypeDeploymentService(typeName, deploymentService)
            val data = ProcessingTypeData.createProcessingTypeData(deploymentManagerProvider, modelData, managerConfig)
            val processingTypes = Map(typeName -> data)
            val combinedData = CombinedProcessingTypeData.create(processingTypes, categoryService)
            new MapBasedProcessingTypeDataProvider(processingTypes, combinedData)
          })
        }
      }

      new NussknackerAppInitializer(appConfig).init(router)
    }
  }

  def run(modelData: ModelData,
          deploymentManagerProvider: DeploymentManagerProvider,
          managerConfig: Config,
          appConfig: Config): Resource[IO, Unit] = {
    //TODO: figure out how to perform e.g. hotswap
    val local = new LocalProcessingTypeDataProviderFactory(modelData, deploymentManagerProvider, managerConfig)
    val app = new NussknackerApp(appConfig, local)
    app.init()
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
