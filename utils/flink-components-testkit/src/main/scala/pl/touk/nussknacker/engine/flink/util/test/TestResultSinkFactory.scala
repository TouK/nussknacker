package pl.touk.nussknacker.engine.flink.util.test

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSink, FlinkLazyParameterFunctionHelper}
import pl.touk.nussknacker.engine.flink.util.test.TestResultSinkFactory.TestResultSink
import pl.touk.nussknacker.engine.testmode.TestRunId

import java.util.concurrent.ConcurrentSkipListMap

class TestResultSinkFactory(runId: TestRunId) extends SinkFactory {

  @MethodToInvoke
  def create(@ParamName("value") value: LazyParameter[AnyRef]): Sink =
    new TestResultSink(value, runId)

}

object TestResultSinkFactory {

  private final case class SinkId(runId: TestRunId) extends Comparable[SinkId] {
    override def compareTo(o: SinkId): Int = runId.id.compareTo(o.runId.id)
  }

  private[TestResultSinkFactory] val sinksOutputs = new ConcurrentSkipListMap[SinkId, Vector[AnyRef]]()

  def extractSinkOutputFor(runId: TestRunId): Output = {
    Option(sinksOutputs.remove(SinkId(runId)))
      .map(l => Output.Present(l.toList))
      .getOrElse(Output.None)
  }

  def clean(runId: TestRunId): Unit = {
    extractSinkOutputFor(runId)
  }

  class TestResultSink(value: LazyParameter[AnyRef], runId: TestRunId) extends BasicFlinkSink with Serializable {

    override type Value = AnyRef

    override def valueFunction(
        helper: FlinkLazyParameterFunctionHelper
    ): FlatMapFunction[Context, ValueWithContext[Value]] =
      helper.lazyMapFunction(value)

    override val toFlinkFunction: SinkFunction[Value] = new SinkFunction[Value] {

      override def invoke(value: Value, context: SinkFunction.Context): Unit = {
        sinksOutputs.compute(
          SinkId(runId),
          (_: SinkId, output: Vector[AnyRef]) => {
            Option(output) match {
              case Some(o) => o :+ value
              case None    => Vector(value)
            }
          }
        )
      }

    }

  }

  sealed trait Output

  object Output {
    case object None                               extends Output
    final case class Present(values: List[AnyRef]) extends Output
  }

}
