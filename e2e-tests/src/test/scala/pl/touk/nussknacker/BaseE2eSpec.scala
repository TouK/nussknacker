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
// `bash -c "export NUSSKNACKER_SCALA_VERSION=2.12 && sbt dist/Docker/publishLocal"`
trait BaseE2eSpec extends BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
  this: Suite =>

  val client: DockerBasedInstallationExampleClient =
    BaseE2eSpec.dockerBasedInstallationExampleNuEnvironmentSingleton.client
}

object BaseE2eSpec extends LazyLogging {

  val dockerBasedInstallationExampleNuEnvironmentSingleton =
    new DockerBasedInstallationExampleNuEnvironment(
      nussknackerImageVersion = BuildInfo.version,
      dockerComposeTweakFiles = List(
        new JFile(Resource.getUrl("bootstrap-setup-scenarios.override.yml").toURI),
        new JFile(Resource.getUrl("batch-nu-designer.override.yml").toURI),
        new JFile(Resource.getUrl("debuggable-nu-designer.override.yml").toURI)
      )
    )

}
