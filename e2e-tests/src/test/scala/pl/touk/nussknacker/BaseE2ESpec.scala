package pl.touk.nussknacker

import better.files._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.installationexample.{
  DockerBasedInstallationExampleClient,
  DockerBasedInstallationExampleNuEnvironment
}

import java.io.{File => JFile}

// Before running tests in this module, a fresh docker image should be built from sources and placed in the local
// registry. If you run tests based on this trait in Intellij Idea and the images is not built, you can do it manually:
// `bash -c "export NUSSKNACKER_SCALA_VERSION=2.13 && sbt dist/Docker/publishLocal"`
trait BaseE2ESpec extends BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
  this: Suite =>

  lazy val client: DockerBasedInstallationExampleClient =
    BaseE2ESpec.dockerBasedInstallationExampleNuEnvironmentSingleton.client

  override def beforeAll(): Unit = {
    client // initialize lazy client
  }

}

object BaseE2ESpec extends LazyLogging {

  private lazy val dockerBasedInstallationExampleNuEnvironmentSingleton: DockerBasedInstallationExampleNuEnvironment = {
    val singleton = new DockerBasedInstallationExampleNuEnvironment(
      nussknackerImageVersion = BuildInfo.version,
      dockerComposeTweakFiles = List(
        new JFile(Resource.getUrl("bootstrap-setup-scenarios.override.yml").toURI),
        new JFile(Resource.getUrl("debuggable-nu-designer.override.yml").toURI)
      )
    )
    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        logger.info("Closing docker compose...")
        singleton.close()
      }
    })
    singleton
  }

}
