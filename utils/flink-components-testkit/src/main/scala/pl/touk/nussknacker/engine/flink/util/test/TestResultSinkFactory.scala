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

  private val sinksOutputs = new ConcurrentSkipListMap[TestRunId, Vector[AnyRef]]()

  def extractOutputFor(runId: TestRunId): Output = {
    Option(sinksOutputs.remove(runId))
      .map(l => Output.Present(l.toList))
      .getOrElse(Output.None)
  }

  def clean(runId: TestRunId): Unit = {
    extractOutputFor(runId)
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
          runId,
          (_: TestRunId, output: Vector[AnyRef]) => {
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
