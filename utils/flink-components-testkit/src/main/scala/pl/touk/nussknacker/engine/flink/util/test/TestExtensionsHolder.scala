package pl.touk.nussknacker.engine.flink.util.test

import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.testmode.TestRunId

import java.util.concurrent.ConcurrentHashMap

// This class is because we don't want Flink to serialize components - we want to allow the communication between
// the test case code and the component code.
case class TestExtensionsHolder(runId: TestRunId) extends Serializable with AutoCloseable {
  def components: List[ComponentDefinition] = TestExtensionsHolder.componentsForId(runId)

  def globalVariables: Map[String, AnyRef] = TestExtensionsHolder.globalVariablesForId(runId)

  def close(): Unit = {
    TestExtensionsHolder.clean(runId)
    TestResultSinkFactory.clean(runId)
  }

}

object TestExtensionsHolder {

  private val extensions = new ConcurrentHashMap[TestRunId, Extensions]()

  private def componentsForId(id: TestRunId): List[ComponentDefinition] = {
    getExtension(id).components
  }

  private def globalVariablesForId(id: TestRunId): Map[String, AnyRef] = {
    getExtension(id).globalVariables
  }

  private def getExtension(id: TestRunId): Extensions = {
    Option(extensions.get(id)).getOrElse(throw new IllegalArgumentException(s"Test run $id doesn't exist"))
  }

  def registerTestExtensions(
      components: List[ComponentDefinition],
      testRunIdAwareComponentCreators: List[TestRunId => ComponentDefinition],
      globalVariables: Map[String, AnyRef]
  ): TestExtensionsHolder = {
    val runId                    = TestRunId.generate
    val testRunIdAwareComponents = testRunIdAwareComponentCreators.map(_.apply(runId))
    extensions.put(runId, Extensions(components ::: testRunIdAwareComponents, globalVariables))
    TestExtensionsHolder(runId)
  }

  private def clean(runId: TestRunId): Unit = {
    extensions.remove(runId)
  }

  private case class Extensions(components: List[ComponentDefinition], globalVariables: Map[String, AnyRef])

}
