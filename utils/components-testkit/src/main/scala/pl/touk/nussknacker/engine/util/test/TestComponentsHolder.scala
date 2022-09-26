package pl.touk.nussknacker.engine.util.test

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{Component, ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.testmode.TestRunId
import pl.touk.nussknacker.engine.util.test.TestComponentsProvider.testRunIdConfig

import java.util.UUID
import scala.reflect.ClassTag

case class TestComponentsHolder(runId: TestRunId) extends Serializable {

  def components[T <: Component : ClassTag]: List[ComponentDefinition] = TestComponentsHolder.componentsForId[T](runId)

  def clean(): Unit = TestComponentsHolder.clean(runId)
}

object TestComponentsHolder {

  private var components = Map[TestRunId, List[ComponentDefinition]]()

  def componentsForId[T <: Component : ClassTag](id: TestRunId): List[ComponentDefinition] = components(id).collect {
    case ComponentDefinition(name, component: T, _, _) => ComponentDefinition(name, component)
  }

  def registerTestComponents(componentDefinitions: List[ComponentDefinition]): TestComponentsHolder = synchronized {
    val runId = TestRunId(UUID.randomUUID().toString)
    components += (runId -> componentDefinitions)
    TestComponentsHolder(runId)
  }

  def clean(runId: TestRunId): Unit = synchronized {
    components -= runId
  }

}

object TestComponentsProvider {

  val name = "test"

  val testRunIdConfig = "testRunId"

}

class TestComponentsProvider extends ComponentProvider {

  override def providerName: String = TestComponentsProvider.name

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    TestComponentsHolder.componentsForId[Component](TestRunId(config.getString(testRunIdConfig)))
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

}
