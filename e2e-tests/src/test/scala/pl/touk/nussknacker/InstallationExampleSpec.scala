package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike

class InstallationExampleSpec extends AnyFreeSpecLike with NuDockerBasedInstallationExample {

  "A test" in {
    sendMessageToKafka("transactions", "message1")
    sendMessageToKafka("transactions", "message2")
//    sendMessageToKafka("transactions", """{ "message": "Test1" }""")
//    sendMessageToKafka("transactions", """{ "message": "Test2" }""")
    println("test")
  }

}
