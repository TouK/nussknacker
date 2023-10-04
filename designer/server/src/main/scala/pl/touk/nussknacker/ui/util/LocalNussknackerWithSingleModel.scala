package pl.touk.nussknacker.ui.util

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigValueFactory._
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData}
import pl.touk.nussknacker.ui.factory.{LocalProcessingTypeDataProviderFactory, NussknackerAppFactory}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters._

//This is helper, which allows for starting UI with given model without having to build jar etc.
// See pl.touk.nussknacker.defaultmodel.RunFlinkStreamingModelLocally for sample usage
object LocalNussknackerWithSingleModel {

  // default name in config
  val typeName = "streaming"

  def run(
      modelData: ModelData,
      deploymentManagerProvider: DeploymentManagerProvider,
      managerConfig: Config,
      categories: Set[String]
  ): Resource[IO, Unit] = {
    for {
      appConfig <- Resource.eval(IO {
        val file: File = prepareUsersFile()
        ConfigFactory
          .parseMap(
            Map[String, Any](
              "authentication.usersFile" -> file.getAbsoluteFile.toURI.toString,
              "categoriesConfig"         -> fromMap(categories.map(cat => cat -> typeName).toMap.asJava)
            ).asJava
          )
      })
      _ <- run(modelData, deploymentManagerProvider, managerConfig, appConfig)
    } yield ()
  }

  def run(
      modelData: ModelData,
      deploymentManagerProvider: DeploymentManagerProvider,
      managerConfig: Config,
      appConfig: Config
  ): Resource[IO, Unit] = {
    // TODO: figure out how to perform e.g. hotswap
    val local      = new LocalProcessingTypeDataProviderFactory(modelData, deploymentManagerProvider, managerConfig)
    val appFactory = new NussknackerAppFactory(local)
    appFactory.createApp(appConfig)
  }

  // TODO: easier way of handling users file
  private def prepareUsersFile(): File = {
    val file = Files.createTempFile("users", "conf").toFile
    FileUtils.write(
      file,
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
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    file.deleteOnExit()
    file
  }

}
