package pl.touk.nussknacker.engine.util.service.query

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.api.process.{ExpressionConfig, ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.{QueryResult, ServiceNotFoundException}
import pl.touk.nussknacker.engine.util.service.query.QueryServiceTesting.{ConcatService, CreateQuery}
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._

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

  it should "return error on failed on not existing service" in {
    assertThrows[IllegalArgumentException](Await.result(invokeConcatService("'fail'", "''"), 1 second))
  }

  it should "throw exception on not existing service" in {
    assertThrows[ServiceNotFoundException](CreateQuery("srv", new ConcatService).invoke("dummy"))
  }

  private def invokeConcatService(s1: String, s2: String) =
    invokeService(new ConcatService, "s1" -> s1, "s2" -> s2)

  private def invokeService(service: Service, args: (String, Expression)*) = {
    CreateQuery("srv", service).invoke("srv", args: _*)
  }

}


object QueryServiceTesting {

  object InvokeService {
    def apply(service: Service, args: (String, Expression)*)
             (implicit executionContext: ExecutionContext): Future[QueryResult] = {
      CreateQuery("srv", service).invoke("srv", args: _*)
    }
  }

  object CreateQuery {
    def apply(serviceName: String, service: Service)
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

}
