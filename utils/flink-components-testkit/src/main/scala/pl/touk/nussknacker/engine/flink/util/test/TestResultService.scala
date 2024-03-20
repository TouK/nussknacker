package pl.touk.nussknacker.engine.flink.util.test

import org.apache.flink.api.common.functions.FlatMapFunction
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory}
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
        case ComponentDefinition(name, component: Service, _, _) if name == TestScenarioRunner.testResultSink =>
          component
      }
      .getOrElse(throw new IllegalStateException(s"No ${TestScenarioRunner.testResultSink} registered"))
      .asInstanceOf[TestResultService]
      .data[R]()
  }

}

class TestResultSink(value: LazyParameter[AnyRef]) extends BasicFlinkSink with Serializable {

  override type Value = AnyRef

  val id = this.hashCode()

  override def valueFunction(
      helper: FlinkLazyParameterFunctionHelper
  ): FlatMapFunction[Context, ValueWithContext[Value]] =
    helper.lazyMapFunction(value)

  override val toFlinkFunction: SinkFunction[Value] = new SinkFunction[Value] {

    override def invoke(value: Value, context: SinkFunction.Context): Unit = {
      val accumulated = TestResultSinkFactory.results.accumulateAndGet(List(value), _ ::: _)
      println(s"[$id] TestResultSink#toFlinkFunction#invoke: $value, accumulated: [${accumulated.mkString(",")}]")
    }

  }

}

class TestResultSinkFactory extends SinkFactory {

  private val createdComponent = new AtomicReference[List[TestResultSink]](List.empty)

  @MethodToInvoke
  def create(@ParamName("value") value: LazyParameter[AnyRef]): Sink = {
    val sink = new TestResultSink(value)
    createdComponent.accumulateAndGet(sink :: Nil, _ ::: _)
    sink
  }
//  override type State = Unit
//
//  private val rawValueParam = ParameterWithExtractor.lazyMandatory[AnyRef]("value")
//
//  override def contextTransformation(context: ValidationContext, dependencies: List[NodeDependencyValue])(
//      implicit nodeId: NodeId
//  ): ContextTransformationDefinition = {
//    case TransformationStep(Nil, _) =>
//      NextParameters(rawValueParam.parameter :: Nil)
//    case TransformationStep(("value", _) :: Nil, _) =>
//      FinalResults(context, List.empty, None)
//  }
//
//  override def implementation(
//      params: Params,
//      dependencies: List[NodeDependencyValue],
//      finalState: Option[State]
//  ): Sink = {
//    val sink = new TestResultSink(???)
//    createdComponent.set(Some(sink))
//    sink
//  }
//
//  override def nodeDependencies: List[NodeDependency] = List.empty

}

object TestResultSinkFactory {

  val results = new AtomicReference[List[AnyRef]](List.empty)

  def extractFromTestComponentsHolder[R](testExtensionsHolder: TestExtensionsHolder): List[R] = {
    results.getAndSet(List.empty).map(_.asInstanceOf[R])
//    testExtensionsHolder.components
//      .collectFirst {
//        case ComponentDefinition(name, component, _, _) if name == TestScenarioRunner.testResultService =>
//          component
//      }
//      .getOrElse(throw new IllegalStateException(s"No ${TestScenarioRunner.testResultService} service registered"))
//      .asInstanceOf[TestResultSinkFactory]
//      .createdComponent
//      .get()
//      .flatMap(_.data[R]())
  }

}
