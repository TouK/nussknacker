package pl.touk.nussknacker.engine.standalone.deployment

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.process.ProcessName

class FileProcessRepositoryTest extends FunSuite with Matchers {

  private val processesDirectory = Files.createTempDirectory("fileProcessRepositoryTest")

  private val repository = new FileProcessRepository(processesDirectory.toFile)

  private val processJson = """{\"additionalBranches\":[],\"nodes\":[{\"ref\":{\"parameters\":[],\"typ\":\"request1-post-source\"},\"id\":\"start\",\"type\":\"Source\"},{\"endResult\":{\"expression\":\"''\",\"language\":\"spel\"},\"ref\":{\"parameters\":[],\"typ\":\"response-sink\"},\"id\":\"endNodeIID\",\"type\":\"Sink\"}],\"exceptionHandlerRef\":{\"parameters\":[]},\"metaData\":{\"typeSpecificData\":{\"path\":\"alamakota\",\"type\":\"StandaloneMetaData\"},\"id\":\"process1\"}}"""
  private val deploymentJson =
    s"""
      |{
      |  "processVersion" : {
      |    "versionId" : 1,
      |    "processName" : "process1",
      |    "user" : "testUser",
      |    "modelVersion" : 3
      |  },
      |  "deploymentTime" : 5,
      |  "processJson":"$processJson"
      |}
      |""".stripMargin

  test("should load deployment data from file") {
    val processName = ProcessName("process1")
    Files.write(processesDirectory.resolve(processName.value), deploymentJson.getBytes(StandardCharsets.UTF_8))

    val deployments = repository.loadAll

    deployments should have size 1
    deployments should contain key (processName)
    val deployment = deployments(processName)
    deployment.processVersion.processName shouldBe processName
    deployment.processVersion.versionId shouldBe 1
    deployment.processVersion.modelVersion shouldBe Some(3)
    deployment.processVersion.user shouldBe "testUser"
    deployment.deploymentTime shouldBe 5
    deployment.processJson shouldBe processJson.replace("\\", "")
  }
}
