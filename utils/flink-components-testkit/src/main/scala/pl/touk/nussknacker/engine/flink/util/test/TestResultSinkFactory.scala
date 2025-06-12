package pl.touk.nussknacker.engine.flink.util.test

import cats.data.NonEmptyList
import com.github.ghik.silencer.silent
import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.flink.api.process.{
  BasicFlinkSink,
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper
}
import pl.touk.nussknacker.engine.flink.util.test.TestResultSinkFactory.TestResultSink
import pl.touk.nussknacker.engine.testmode.TestRunId

import java.util.concurrent.ConcurrentHashMap

// `TestResultSinkFactory` is closely related to the ID of the running test. We use the ID to extract results from
// the shared map with results collected from all instances of TestResultSink (see `TestResultSinkFactory.sinksOutputs`)
// We have to do it like this because there is no other way to intercept data flowing out of the Flink's sink
class TestResultSinkFactory(runId: TestRunId) extends SinkFactory {

  @MethodToInvoke
  def create(@ParamName("value") value: LazyParameter[AnyRef]): Sink =
    new TestResultSink(value, runId)

}

object TestResultSinkFactory {

  private val sinksOutputs = new ConcurrentHashMap[TestRunId, NonEmptyList[AnyRef]]()

  def extractOutputFor(runId: TestRunId): Output = {
    Option(sinksOutputs.remove(runId))
      .map(l => Output.Available(l))
      .getOrElse(Output.NotAvailable)
  }

  def clean(runId: TestRunId): Unit = {
    sinksOutputs.remove(runId)
  }

  class TestResultSink(value: LazyParameter[AnyRef], runId: TestRunId) extends BasicFlinkSink with Serializable {

    override type Value = AnyRef

    override def valueFunction(
        helper: FlinkLazyParameterFunctionHelper
    ): FlatMapFunction[Context, ValueWithContext[Value]] =
      helper.lazyMapFunction(value)

    @silent("deprecated")
    override def toFlinkFunction(flinkNodeContext: FlinkCustomNodeContext): SinkFunction[Value] =
      new SinkFunction[Value] {

        override def invoke(value: Value, context: SinkFunction.Context): Unit = {
          sinksOutputs.compute(
            runId,
            (_: TestRunId, output: NonEmptyList[AnyRef]) => {
              Option(output) match {
                case Some(o) => o :+ value
                case None    => NonEmptyList.one(value)
              }
            }
          )
        }

      }

  }

  sealed trait Output

  object Output {
    case object NotAvailable                                 extends Output
    final case class Available(values: NonEmptyList[AnyRef]) extends Output
  }

}
