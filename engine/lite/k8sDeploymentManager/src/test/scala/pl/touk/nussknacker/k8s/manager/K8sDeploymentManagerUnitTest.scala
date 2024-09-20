package pl.touk.nussknacker.k8s.manager

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.{
  labelsForScenario,
  nussknackerInstanceNameLabel,
  objectNameForScenario,
  scenarioIdLabel,
  scenarioNameLabel,
  scenarioVersionLabel
}
import org.scalatest.prop.TableDrivenPropertyChecks._

import scala.collection.compat.immutable.LazyList.continually

class K8sDeploymentManagerUnitTest extends AnyFunSuite with Matchers {

  private def versionForName(name: String) =
    ProcessVersion(VersionId(13), ProcessName(name), ProcessId(256), List.empty, "user", None)

  private def nameOfLength(count: Int) = continually("a").take(count).mkString

  val commonLabels = Map(
    scenarioVersionLabel -> "13",
    scenarioIdLabel      -> "256"
  )

  test("should generate correct labels for scenario names") {

    val names = Table(
      ("scenario name", "name label"),
      ("standard", "standard-fe6d3468cf"),
      ("ała", "a-a-b7ec948d4b"),
      // same as above after sanitize, but hash is different
      ("a-a", "a-a-199d9b3dfc"),
      (nameOfLength(81), s"${nameOfLength(52)}-8c48280d57"),
      // same as above after sanitize, but hash is different
      (nameOfLength(80), s"${nameOfLength(52)}-0f45e858fb"),
    )
    forAll(names) { (scenarioName: String, nameLabel: String) =>
      val generated = labelsForScenario(versionForName(scenarioName), None)
      generated.values.foreach { value =>
        value.length should be <= K8sUtils.maxObjectNameLength
      }
      generated shouldBe commonLabels +
        (scenarioNameLabel -> nameLabel)
    }
  }

  test("should generate labels with instance name when instance name is provided") {
    val nussknackerInstanceName = "foo-release"
    val generated               = labelsForScenario(versionForName("standard"), Some(nussknackerInstanceName))

    generated shouldBe commonLabels +
      (scenarioNameLabel            -> "standard-fe6d3468cf") +
      (nussknackerInstanceNameLabel -> nussknackerInstanceName)
  }

  test("should generate correct object id for scenario names") {

    val names = Table(
      ("scenario name", "hashInput", "object id"),
      ("standard", None, "scenario-256-standard"),
      ("ała", None, "scenario-256-a-a"),
      (nameOfLength(81), None, s"scenario-256-${nameOfLength(50)}"),
      // here we don't care about hash, as id should be unique
      ("a-a", None, "scenario-256-a-a"),
      // but for different content, id should be different
      ("st", Some("version1"), "scenario-256-st-fc6af3ec64"),
      ("st", Some("version2"), "scenario-256-st-366a963b60"),
    )

    forAll(names) { (scenarioName: String, hashInput: Option[String], expectedId: String) =>
      val generated = objectNameForScenario(versionForName(scenarioName), None, hashInput)
      generated.length should be <= K8sUtils.maxObjectNameLength
      generated shouldBe expectedId
    }
  }

  test("should generate correct object id for scenario names with nussknacker instance name") {

    val names = Table(
      ("scenario name", "hashInput", "object id", "nussknacker instance name"),
      ("standard", None, "x-scenario-256-standard", Some("")),
      ("standard", None, "nu1-scenario-256-standard", Some("nu1"))
    )

    forAll(names) {
      (scenarioName: String, hashInput: Option[String], expectedId: String, nussknackerInstanceName: Option[String]) =>
        val generated = objectNameForScenario(versionForName(scenarioName), nussknackerInstanceName, hashInput)
        generated.length should be <= K8sUtils.maxObjectNameLength
        generated shouldBe expectedId
    }
  }

}
