package pl.touk.nussknacker.engine.benchmarks.e2e

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.installationexample.DockerBasedInstallationExampleNuEnvironment.fileFromResourceStream
import pl.touk.nussknacker.test.installationexample.{
  DockerBasedInstallationExampleClient,
  DockerBasedInstallationExampleNuEnvironment
}

// Before running benchmarks in this module, a fresh docker image should be built from sources and placed in the local
// registry. If you run tests based on this trait in Intellij Idea and the images is not built, you can do it manually:
// `bash -c "export NUSSKNACKER_SCALA_VERSION=2.12 && sbt dist/Docker/publishLocal"`
trait BaseE2EBenchmark {

  val client: DockerBasedInstallationExampleClient =
    BaseE2EBenchmark.dockerBasedInstallationExampleNuEnvironmentSingleton.client

}

object BaseE2EBenchmark extends LazyLogging {

  val dockerBasedInstallationExampleNuEnvironmentSingleton =
    new DockerBasedInstallationExampleNuEnvironment(
      nussknackerImageVersion = BuildInfo.version,
      dockerComposeTweakFiles = List(
        fileFromResourceStream(BaseE2EBenchmark.getClass.getResourceAsStream("/benchmark-setup.override.yml"))
      )
    )

}
