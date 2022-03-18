package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.WithCategories

import scala.reflect.ClassTag

case class MockComponentHolder(runId: TestRunId) extends Serializable {

  def components[T <: Component: ClassTag]: Map[String, WithCategories[T]] = MockComponentsHolder.resultsForId[T](runId)

  def clean(): Unit = ResultsCollectingListenerHolder.cleanResult(runId)
}


object MockComponentsHolder {

  val testRunId: TestRunId = TestRunId("mock")
  //this not necessarily must be a map
  private var results = Map[TestRunId, Map[String, WithCategories[Component]]]()

  def resultsForId[T <: Component : ClassTag](id: TestRunId): Map[String, WithCategories[T]] = results(id).collect {
    case (id, a@WithCategories(definition: T, _, _)) => id -> a.copy(value = definition)
  }

  def registerMockComponents(mockServices: Map[String, WithCategories[Component]]): MockComponentHolder = synchronized {
    results += (testRunId -> mockServices)
    MockComponentHolder(testRunId)
  }

  def cleanResult(runId: TestRunId): Unit = synchronized {
    results -= runId
  }

}
