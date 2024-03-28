package pl.touk.nussknacker.engine.flink.util.test

import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.testmode.TestRunId

// This class is because we don't want Flink to serialize components - we want to allow the communication between
// the test case code and the component code.
case class TestExtensionsHolder(runId: TestRunId) extends Serializable with AutoCloseable {
  def components: List[ComponentDefinition] = TestExtensionsHolder.componentsForId(runId)

  def globalVariables: Map[String, AnyRef] = TestExtensionsHolder.globalVariablesForId(runId)

  def close(): Unit = TestExtensionsHolder.clean(runId)

}

object TestExtensionsHolder {

  private var extensions = Map[TestRunId, Extensions]()

  private def componentsForId(id: TestRunId): List[ComponentDefinition] = synchronized {
    extensions(id).components
  }

  private def globalVariablesForId(id: TestRunId): Map[String, AnyRef] = synchronized {
    extensions(id).globalVariables
  }

  def registerTestExtensions(
      componentsCreators: List[TestRunId => ComponentDefinition],
      globalVariables: Map[String, AnyRef]
  ): TestExtensionsHolder = synchronized {
    val runId = TestRunId.generate
    extensions += (runId -> Extensions(componentsCreators.map(_.apply(runId)), globalVariables))
    TestExtensionsHolder(runId)
  }

  private def clean(runId: TestRunId): Unit = synchronized {
    extensions -= runId
  }

  private case class Extensions(components: List[ComponentDefinition], globalVariables: Map[String, AnyRef])

}
