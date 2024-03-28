package pl.touk.nussknacker.engine.flink.util.test

import cats.kernel.Semigroup
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

  private[TestResultSinkFactory] val sinksOutputs = new ConcurrentSkipListMap[SinkId, Output]()

  def extractSinkOutputFor(runId: TestRunId): Output = {
    Option(sinksOutputs.remove(SinkId(runId))).getOrElse(Output.None)
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
          (_: SinkId, output: Output) => {
            Option(output) match {
              case Some(o) => Output.semigroup.combine(o, Output.Present(List(value)))
              case None    => Output.Present(List(value))
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

    implicit val semigroup: Semigroup[Output] = Semigroup.instance {
      case (Output.None, Output.None)                         => Output.None
      case (Output.None, presentOutput: Output.Present)       => presentOutput
      case (presentOutput: Output.Present, Output.None)       => presentOutput
      case (Output.Present(values1), Output.Present(values2)) => Output.Present(values1 ::: values2)
    }

  }

}
