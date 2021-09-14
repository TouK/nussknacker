package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.ConfigFactory

import java.nio.file.{Files, Path, Paths}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.management.periodic.flink.FlinkJarManager
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import pl.touk.nussknacker.test.PatientScalaFutures

class JarManagerTest extends FunSuite
  with Matchers
  with ScalaFutures
  with PatientScalaFutures {

  private val processName = "test"
  private val processVersionId = 5
  private val processVersion = ProcessVersion.empty.copy(processName = ProcessName(processName), versionId = processVersionId)
  private val processJson = "{}"
  private val jarsDir = Files.createTempDirectory("jars-dir")
  private val modelJarFileContent = "abc".getBytes
  private val currentModelJarFile = {
    val tempFile = Files.createTempFile("currentModelJarFile", ".jar")
    Files.write(tempFile, modelJarFileContent)
    tempFile.toFile
  }

  private val jarManager = createJarManager(jarsDir = jarsDir)

  private def createJarManager(jarsDir: Path): JarManager = {
    new FlinkJarManager(
      flinkClient = new FlinkClientStub,
      jarsDir = jarsDir,
      inputConfigDuringExecution = InputConfigDuringExecution(ConfigFactory.empty()),
      createCurrentModelJarFile = currentModelJarFile
    )
  }

  test("prepareDeploymentWithJar - should copy to local dir") {
    val result = jarManager.prepareDeploymentWithJar(processVersion, processJson)

    val copiedJarFileName = result.futureValue.jarFileName
    copiedJarFileName should include (processName)
    copiedJarFileName should include (processVersionId.toString)
    val copiedJarFile = jarsDir.resolve(copiedJarFileName)
    Files.exists(copiedJarFile) shouldBe true
    Files.readAllBytes(copiedJarFile) shouldBe modelJarFileContent
  }

  test("prepareDeploymentWithJar - should create jars dir if not exists") {
    val tmpDir = System.getProperty("java.io.tmpdir")
    val jarsDir = Paths.get(tmpDir, s"jars-dir-not-exists-${System.currentTimeMillis()}")
    val jarManager = createJarManager(jarsDir = jarsDir)

    Files.exists(jarsDir) shouldBe false

    val result = jarManager.prepareDeploymentWithJar(processVersion, processJson)

    val copiedJarFileName = result.futureValue.jarFileName
    Files.exists(jarsDir) shouldBe true
    Files.exists(jarsDir.resolve(copiedJarFileName)) shouldBe true
  }

  test("deleteJar - should delete both local and Flink jar") {
    val jarFileName = s"${System.currentTimeMillis()}.jar"
    val jarPath = jarsDir.resolve(jarFileName)
    Files.copy(currentModelJarFile.toPath, jarPath)

    jarManager.deleteJar(jarFileName).futureValue

    Files.exists(jarPath) shouldBe false
  }

  test("deleteJar - should handle not existing file") {
    val result = jarManager.deleteJar("unknown.jar").futureValue

    result shouldBe (())
  }
}
