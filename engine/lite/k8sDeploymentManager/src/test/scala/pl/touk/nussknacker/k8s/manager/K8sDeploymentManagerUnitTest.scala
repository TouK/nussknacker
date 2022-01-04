package pl.touk.nussknacker.k8s.manager

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{labelsForScenario, objectNameForScenario, scenarioIdLabel, scenarioNameLabel, scenarioVersionLabel}
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManagerUnitTest.maxK8sLength

import scala.collection.immutable.Stream.continually

object K8sDeploymentManagerUnitTest {

  private val maxK8sLength = 63

}

class K8sDeploymentManagerUnitTest extends FunSuite with Matchers {

  private def versionForName(name: String) = ProcessVersion(VersionId(13), ProcessName(name), ProcessId(256), "user", None)

  private def nameOfLength(count: Int) = continually("a").take(count).mkString


  test("should generate correct labels for scenario names") {

    val names = Table(
      ("scenario name", "name label"),
      ("standard", "standard-fe6d3468cf"),
      ("ała", "a-a-b7ec948d4b"),
      //same as above after sanitize, but hash is different    
      ("a-a", "a-a-199d9b3dfc"),
      (nameOfLength(81), s"${nameOfLength(52)}-8c48280d57"),
      //same as above after sanitize, but hash is different
      (nameOfLength(80), s"${nameOfLength(52)}-0f45e858fb"),

    )
    forAll(names) { (scenarioName: String, nameLabel: String) =>
      val generated = labelsForScenario(versionForName(scenarioName))
      generated.values.foreach { value =>
        value.length should be <= maxK8sLength
      }
      generated shouldBe Map(
        scenarioNameLabel -> nameLabel,
        scenarioVersionLabel -> "13",
        scenarioIdLabel -> "256"
      )
    }
  }

  test("should generate correct object id for scenario names") {

    val names = Table(
      ("scenario name", "hashInput", "object id"),
      ("standard", None, "scenario-256-standard"),
      ("ała", None, "scenario-256-a-a"),
      (nameOfLength(81), None, s"scenario-256-${nameOfLength(50)}"),
      //here we don't care about hash, as id should be unique
      ("a-a", None, "scenario-256-a-a"),
      //but for different content, id should be different
      ("st", Some("version1"), "scenario-256-st-fc6af3ec64"),
      ("st", Some("version2"), "scenario-256-st-366a963b60"),
    )
    
    forAll(names) { (scenarioName: String, hashInput: Option[String], expectedId: String) =>
      val generated = objectNameForScenario(versionForName(scenarioName), hashInput)
      generated.length should be <= maxK8sLength
      generated shouldBe expectedId
    }
  }

  

}
