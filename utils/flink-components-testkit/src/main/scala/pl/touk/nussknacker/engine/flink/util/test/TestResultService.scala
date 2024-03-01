package pl.touk.nussknacker.engine.flink.util.test

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{BasicFlinkSink, FlinkLazyParameterFunctionHelper}
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner

import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.{ExecutionContext, Future}

class TestResultService extends Service {

  private var invocationResult: List[Any] = List()

  @MethodToInvoke
  def invoke(@ParamName("value") value: Any)(implicit ec: ExecutionContext): Future[Unit] = {

    Future.successful {
      invocationResult = value :: invocationResult
    }
  }

  def data[T](): List[T] = invocationResult.reverse.map(_.asInstanceOf[T])

}

object TestResultService {

  def extractFromTestComponentsHolder[R](testExtensionsHolder: TestExtensionsHolder): List[R] = {
    testExtensionsHolder.components
      .collectFirst {
        case ComponentDefinition(name, component: Service, _, _) if name == TestScenarioRunner.testResultService =>
          component
      }
      .getOrElse(throw new IllegalStateException(s"No ${TestScenarioRunner.testResultService} service registered"))
      .asInstanceOf[TestResultService]
      .data[R]()
  }

}

class TestResultSink extends BasicFlinkSink with Serializable {

  override type Value = AnyRef

  override def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[Value]] =
    new FlatMapFunction[Context, ValueWithContext[Value]] {

      override def flatMap(value: Context, out: Collector[ValueWithContext[Value]]): Unit = {
        println(s"TestResultSink#valueFunction#flatMap: $value")
      }

    }

  override val toFlinkFunction: SinkFunction[Value] = new SinkFunction[Value] {

    override def invoke(value: Value, context: SinkFunction.Context): Unit = {
      println(s"TestResultSink#toFlinkFunction#invoke: $value")
    }

  }

  def data[T](): List[T] =
    List.empty
}

class TestResultSinkFactory extends SinkFactory {

  private val createdComponent = new AtomicReference[Option[TestResultSink]](None)

  @MethodToInvoke
  def create(@ParamName("value") value: Any): Sink = {
    val sink = new TestResultSink()
    createdComponent.set(Some(sink))
    sink
  }

}

object TestResultSinkFactory {

  def extractFromTestComponentsHolder[R](testExtensionsHolder: TestExtensionsHolder): List[R] = {
    testExtensionsHolder.components
      .collectFirst {
        case ComponentDefinition(name, component, _, _) if name == TestScenarioRunner.testResultService =>
          component
      }
      .getOrElse(throw new IllegalStateException(s"No ${TestScenarioRunner.testResultService} service registered"))
      .asInstanceOf[TestResultSinkFactory]
      .createdComponent
      .get()
      .get
      .data[R]()
  }

}
