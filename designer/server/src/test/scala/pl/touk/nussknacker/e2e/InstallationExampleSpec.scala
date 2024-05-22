package pl.touk.nussknacker.e2e

import org.scalatest.freespec.AnyFreeSpecLike

class InstallationExampleSpec extends AnyFreeSpecLike with NuDockerBasedInstallationExample {

  "A test" in {
    val ll = container.getContainerByServiceName("akhq")
    println("test")
  }

}
