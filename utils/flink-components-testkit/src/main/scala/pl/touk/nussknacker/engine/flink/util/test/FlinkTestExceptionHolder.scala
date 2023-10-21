package pl.touk.nussknacker.engine.flink.util.test

import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.testmode.TestRunId

final class FlinkTestExceptionHolder(testRunId: TestRunId) extends Serializable with AutoCloseable {

  def addException(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit =
    FlinkTestExceptionHolder.addException(testRunId, exceptionInfo)

  def getExceptions: List[NuExceptionInfo[_ <: Throwable]] =
    FlinkTestExceptionHolder.exceptionsForId(testRunId)

  def close(): Unit = FlinkTestExceptionHolder.clean(testRunId)

}

object FlinkTestExceptionHolder {

  private var exceptions = Map[TestRunId, List[NuExceptionInfo[_ <: Throwable]]]().withDefaultValue(Nil)

  def register() =
    new FlinkTestExceptionHolder(TestRunId.generate)

  private def exceptionsForId(testRunId: TestRunId): List[NuExceptionInfo[_ <: Throwable]] =
    exceptions(testRunId)

  private def addException(testRunId: TestRunId, exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = synchronized {
    exceptions += (testRunId -> (exceptions(testRunId) ++ List(exceptionInfo)))
  }

  private def clean(runId: TestRunId): Unit = synchronized {
    exceptions -= runId
  }

}
