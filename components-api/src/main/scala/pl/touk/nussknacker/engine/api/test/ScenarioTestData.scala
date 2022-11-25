package pl.touk.nussknacker.engine.api.test

import pl.touk.nussknacker.engine.api.NodeId

import java.nio.charset.StandardCharsets

sealed trait ScenarioTestData {
  def samplesLimit: Int
  def forSourceId(id: NodeId): TestData
}

case class SingleSourceScenarioTestData(testData: TestData, samplesLimit: Int) extends ScenarioTestData {
  override def forSourceId(id: NodeId): TestData = testData
}

// TODO: string key?
case class MultipleSourcesScenarioTestData(sourceTestDataMap: Map[String, TestData], samplesLimit: Int) extends ScenarioTestData {
  override def forSourceId(id: NodeId): TestData = sourceTestDataMap.getOrElse(id.id, throw new IllegalArgumentException(s"Missing test data for: $id"))
}

object ScenarioTestData {
  def newLineSeparated(s: String*): ScenarioTestData = SingleSourceScenarioTestData(TestData(s.mkString("\n").getBytes(StandardCharsets.UTF_8)), s.length)
}

