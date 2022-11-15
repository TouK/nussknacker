package pl.touk.nussknacker.engine.api.test

import pl.touk.nussknacker.engine.api.NodeId

import java.nio.charset.StandardCharsets

// TODO: remove samplesLimit
// TODO: differentiate raw test data as json, string, bytes, etc.
case class TestData(testData: Array[Byte], samplesLimit: Int)

trait ScenarioTestData {
  def samplesLimit: Int
  def forNodeId(id: NodeId): TestData
}

case class SingleSourceScenarioTestData(testData: TestData, samplesLimit: Int) extends ScenarioTestData {
  override def forNodeId(id: NodeId): TestData = testData
}

// TODO: string key
case class MultipleSourcesScenarioTestData(sourceTestDataMap: Map[String, TestData], samplesLimit: Int) extends ScenarioTestData {
  override def forNodeId(id: NodeId): TestData = sourceTestDataMap(id.id)
}

object TestData {
  def newLineSeparated(s: String*): ScenarioTestData = SingleSourceScenarioTestData(TestData(s.mkString("\n").getBytes(StandardCharsets.UTF_8), s.length), s.length)
}
