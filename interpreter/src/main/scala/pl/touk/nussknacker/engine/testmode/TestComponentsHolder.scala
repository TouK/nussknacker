package pl.touk.nussknacker.engine.testmode

import pl.touk.nussknacker.engine.api.component.Component
import pl.touk.nussknacker.engine.api.process.WithCategories

import java.util.UUID
import scala.reflect.ClassTag

case class TestComponentHolder(runId: TestRunId) extends Serializable {

  def components[T <: Component: ClassTag]: Map[String, WithCategories[T]] = TestComponentsHolder.resultsForId[T](runId)

  def clean(): Unit = TestComponentsHolder.cleanResult(runId)
}

object TestComponentsHolder {

  private var results = Map[TestRunId, Map[String, WithCategories[Component]]]()

  def resultsForId[T <: Component : ClassTag](id: TestRunId): Map[String, WithCategories[T]] = results(id).collect {
    case (id, a@WithCategories(definition: T, _, _)) => id -> a.copy(value = definition)
  }

  def registerMockComponents(mockServices: Map[String, WithCategories[Component]]): TestComponentHolder = synchronized {
    val runId = TestRunId(UUID.randomUUID().toString)
    results += (runId -> mockServices)
    TestComponentHolder(runId)
  }

  def cleanResult(runId: TestRunId): Unit = synchronized {
    results -= runId
  }

}
