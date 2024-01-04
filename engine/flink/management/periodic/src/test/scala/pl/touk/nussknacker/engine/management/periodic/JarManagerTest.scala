package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{MetaData, ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.management.FlinkModelJarProvider
import pl.touk.nussknacker.engine.management.periodic.flink.FlinkJarManager
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import pl.touk.nussknacker.test.PatientScalaFutures

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.Future

class JarManagerTest extends AnyFunSuite with Matchers with ScalaFutures with PatientScalaFutures {

  private val processName      = "test"
  private val processVersionId = 5
  private val processVersion =
    ProcessVersion.empty.copy(processName = ProcessName(processName), versionId = VersionId(processVersionId))
  private val process             = CanonicalProcess(MetaData("foo", StreamMetaData()), Nil)
  private val jarsDir             = Files.createTempDirectory("jars-dir")
  private val modelJarFileContent = "abc".getBytes

  private val currentModelJarFile = {
    val tempFile = Files.createTempFile("currentModelJarFile", ".jar")
    Files.write(tempFile, modelJarFileContent)
    tempFile.toFile
  }

  private val currentModelUrls = List(currentModelJarFile.toURI.toURL)

  private val jarManager = createJarManager(jarsDir = jarsDir)

  private def createJarManager(
      jarsDir: Path,
      modelJarProvider: FlinkModelJarProvider = new FlinkModelJarProvider(currentModelUrls)
  ): JarManager = {

    new FlinkJarManager(
      flinkClient = new FlinkClientStub,
      jarsDir = jarsDir,
      inputConfigDuringExecution = InputConfigDuringExecution(ConfigFactory.empty()),
      modelJarProvider = modelJarProvider
    )
  }

  test("prepareDeploymentWithJar - should copy to local dir") {
    val result = jarManager.prepareDeploymentWithJar(processVersion, process)

    val copiedJarFileName = result.futureValue.jarFileName
    copiedJarFileName should fullyMatch regex s"^$processName-$processVersionId-\\d+\\.jar$$"
    val copiedJarFile = jarsDir.resolve(copiedJarFileName)
    Files.exists(copiedJarFile) shouldBe true
    Files.readAllBytes(copiedJarFile) shouldBe modelJarFileContent
  }

  test("prepareDeploymentWithJar - should handle disappearing model JAR") {
    val modelJarProvider = new FlinkModelJarProvider(currentModelUrls)
    val jarManager       = createJarManager(jarsDir, modelJarProvider)

    def verifyAndDeleteJar(result: Future[DeploymentWithJarData]): Unit = {
      val copiedJarFile = jarsDir.resolve(result.futureValue.jarFileName)
      Files.exists(copiedJarFile) shouldBe true
      Files.readAllBytes(copiedJarFile) shouldBe modelJarFileContent
      Files.delete(copiedJarFile)
    }

    verifyAndDeleteJar(jarManager.prepareDeploymentWithJar(processVersion, process))

    modelJarProvider.getJobJar().delete() shouldBe true

    verifyAndDeleteJar(jarManager.prepareDeploymentWithJar(processVersion, process))
  }

  test("prepareDeploymentWithJar - should create jars dir if not exists") {
    val tmpDir     = System.getProperty("java.io.tmpdir")
    val jarsDir    = Paths.get(tmpDir, s"jars-dir-not-exists-${System.currentTimeMillis()}")
    val jarManager = createJarManager(jarsDir = jarsDir)

    Files.exists(jarsDir) shouldBe false

    val result = jarManager.prepareDeploymentWithJar(processVersion, process)

    val copiedJarFileName = result.futureValue.jarFileName
    Files.exists(jarsDir) shouldBe true
    Files.exists(jarsDir.resolve(copiedJarFileName)) shouldBe true
  }

  test("deleteJar - should delete both local and Flink jar") {
    val jarFileName = s"${System.currentTimeMillis()}.jar"
    val jarPath     = jarsDir.resolve(jarFileName)
    Files.copy(currentModelJarFile.toPath, jarPath)

    jarManager.deleteJar(jarFileName).futureValue

    Files.exists(jarPath) shouldBe false
  }

  test("deleteJar - should handle not existing file") {
    val result = jarManager.deleteJar("unknown.jar").futureValue

    result shouldBe (())
  }

}
