package pl.touk.nussknacker.engine.lite.kafka

import io.circe.syntax._
import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

import java.io.File
import java.nio.charset.StandardCharsets

object NuRuntimeTestUtils {

  val deploymentDataFile: File = new File(getClass.getResource("/sampleDeploymentData.conf").getFile)

  def testCaseId(suiteName: String, scenario: CanonicalProcess): String = testCaseId(suiteName, scenario.id)

  def testCaseId(suiteName: String, scenarioId: String): String = suiteName + "-" + scenarioId

  def saveScenarioToTmp(scenario: CanonicalProcess, scenarioFilePrefix: String): File = {
    val jsonFile = File.createTempFile(scenarioFilePrefix, ".json")
    jsonFile.deleteOnExit()
    FileUtils.write(jsonFile, scenario.asJson.spaces2, StandardCharsets.UTF_8)
    jsonFile
  }

}
