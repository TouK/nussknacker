package pl.touk.nussknacker.engine.util.service.query

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, SingleInputGenericNodeTransformation}
import pl.touk.nussknacker.engine.api.definition.{NodeDependency, OutputVariableNameDependency, ParameterWithExtractor}
import pl.touk.nussknacker.engine.api.process.{ComponentUseCase, EmptyProcessConfigCreator, ExpressionConfig, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.util.service.query.QueryServiceTesting.{CollectingDynamicEagerService, CollectingEagerService, ConcatService, CreateQuery}
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.{QueryResult, ServiceNotFoundException}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class ServiceQuerySpec extends FlatSpec with Matchers with PatientScalaFutures {

  import pl.touk.nussknacker.engine.spel.Implicits._

  override def spanScaleFactor: Double = 2

  private implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx

  it should "evaluate spel expressions" in {
    invokeConcatService("'foo'", "'bar'").futureValue.result shouldBe "foobar"
  }

  it should "evaluate spel expressions with math expression" in {
    invokeConcatService("'foo'", "(1 + 2).toString()").futureValue.result shouldBe "foo3"
  }

  it should "allow using global variables" in {
    invokeConcatService("'foo'", "#GLOBAL").futureValue.result shouldBe "fooglobalValue"
  }

  it should "evaluate spel expressions using provided local context variables" in {
    CreateQuery("srv", new ConcatService).invoke("srv", localVariables = Map("var" -> ("foo", Typed[String])), "s1" -> "#var", "s2" -> "'bar'")
      .futureValue.result shouldBe "foobar"
  }

  it should "return error on failed on not existing service" in {
    assertThrows[IllegalArgumentException](Await.result(invokeConcatService("'fail'", "''"), 1 second))
  }

  it should "throw exception on not existing service" in {
    assertThrows[ServiceNotFoundException](Await.result(CreateQuery("srv", new ConcatService).invoke("dummy"), 1 second))
  }

  it should "invoke eager service" in {
    List(CollectingDynamicEagerService, CollectingEagerService).foreach { service =>
      invokeService(service, "static" -> "'s'", "dynamic" -> "'d'").futureValue.result shouldBe "static-s-dynamic-d"
    }
  }

  private def invokeConcatService(s1: String, s2: String) =
    invokeService(new ConcatService, "s1" -> s1, "s2" -> s2)

  private def invokeService(service: Service, args: (String, Expression)*) = {
    CreateQuery("srv", service).invoke("srv", args: _*)
  }

}


object QueryServiceTesting {

  class ConcatService extends Service {
    @MethodToInvoke
    def concat(@ParamName("s1") s1: String, @ParamName("s2") s2: String)
              (implicit executionContext: ExecutionContext): Future[String] = {
      if (s1 == "fail") {
        Future.failed(new IllegalArgumentException("Fail"))
      } else {
        Future(s1 + s2)
      }
    }
  }

  class CollectingEagerInvoker(static: String) extends ServiceInvoker {
    override def invokeService(params: Map[String, Any])(implicit ec: ExecutionContext,
                                                         collector: ServiceInvocationCollector,
                                                         contextId: ContextId,
                                                         componentUseCase: ComponentUseCase): Future[Any] = {
      val returnValue = s"static-$static-dynamic-${params("dynamic")}"
      collector.collect("mocked" + returnValue, Option("")) {
        Future.successful(returnValue)
      }
    }

  }

  object InvokeService {
    def apply(service: Service, args: (String, Expression)*)
             (implicit executionContext: ExecutionContext): Future[QueryResult] = {
      CreateQuery("srv", service).invoke("srv", args: _*)
    }
  }

  object CreateQuery {
    def apply(serviceName: String, service: Service, localVariables: Map[String, Any] = Map.empty)
             (implicit executionContext: ExecutionContext): ServiceQuery = {
      new ServiceQuery(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator {

        override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
          super.expressionConfig(processObjectDependencies).copy(globalProcessVariables = Map("GLOBAL" -> WithCategories("globalValue")))
        }

        override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
          super.services(processObjectDependencies) ++ Map(serviceName -> WithCategories(service))
      }))
    }
  }

  object CollectingEagerService extends EagerService {

    @MethodToInvoke
    def invoke(@ParamName("static") static: String,
               @ParamName("dynamic") dynamic: LazyParameter[String]): ServiceInvoker = new CollectingEagerInvoker(static)

  }

  object CollectingDynamicEagerService extends EagerService with SingleInputGenericNodeTransformation[ServiceInvoker] {

    override type State = Nothing
    private val static = ParameterWithExtractor.mandatory[String]("static")
    private val dynamic = ParameterWithExtractor.lazyMandatory[String]("dynamic")

    override def contextTransformation(context: ValidationContext,
                                       dependencies: List[NodeDependencyValue])
                                      (implicit nodeId: NodeId): CollectingDynamicEagerService.NodeTransformationDefinition = {
      case TransformationStep(Nil, _) => NextParameters(List(static.parameter, dynamic.parameter))
      case TransformationStep(_, _) => FinalResults.forValidation(context)(
        _.withVariable(OutputVariableNameDependency.extract(dependencies), Typed[String], None))
    }

    override def implementation(params: Map[String, Any],
                                dependencies: List[NodeDependencyValue],
                                finalState: Option[State]): ServiceInvoker = new CollectingEagerInvoker(static.extractValue(params))

    override def nodeDependencies: List[NodeDependency] = List(OutputVariableNameDependency)
  }
}
