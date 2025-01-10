package pl.touk.nussknacker.ui

import cats.effect.{IO, Resource}
import com.typesafe.config.{Config, ConfigFactory, ConfigValue, ConfigValueFactory}
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData}
import pl.touk.nussknacker.ui.config.{DesignerConfig, SimpleConfigLoadingDesignerConfigLoader}
import pl.touk.nussknacker.ui.factory.NussknackerAppFactory
import pl.touk.nussknacker.ui.process.processingtype.loader.LocalProcessingTypeDataLoader

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import scala.jdk.CollectionConverters._

//This is helper, which allows for starting UI with given model without having to build jar etc.
// See pl.touk.nussknacker.defaultmodel.RunFlinkStreamingModelLocally for sample usage
object LocalNussknackerWithSingleModel {

  // default name in config
  val typeName = "streaming"
  val category = "Default"

  def run(
      modelData: ModelData,
      deploymentManagerProvider: DeploymentManagerProvider
  ): Resource[IO, Unit] = {
    for {
      appConfig <- Resource.eval(IO {
        val file: File = prepareUsersFile()
        ConfigFactory
          .parseMap(
            Map[String, Any](
              "authentication.usersFile" -> file.getAbsoluteFile.toURI.toString
            ).asJava
          )
      })
      _ <- run(modelData, deploymentManagerProvider, appConfig)
    } yield ()
  }

  def run(
      modelData: ModelData,
      deploymentManagerProvider: DeploymentManagerProvider,
      appConfig: Config
  ): Resource[IO, Unit] = {
    // TODO: figure out how to perform e.g. hotswap
    val local = new LocalProcessingTypeDataLoader(
      modelData = Map(typeName -> (category, modelData)),
      deploymentManagerProvider = deploymentManagerProvider
    )
    val designerConfig = DesignerConfig.from(
      // This map is ignored but must exist
      appConfig.withValue("scenarioTypes", ConfigValueFactory.fromMap(Map.empty[String, ConfigValue].asJava))
    )
    val appFactory = new NussknackerAppFactory(
      new SimpleConfigLoadingDesignerConfigLoader(designerConfig.rawConfig.resolved),
      _ => local
    )
    appFactory.createApp()
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
