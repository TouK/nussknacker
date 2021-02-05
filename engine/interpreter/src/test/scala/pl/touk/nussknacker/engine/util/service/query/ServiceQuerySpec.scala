package pl.touk.nussknacker.engine.util.service.query


import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.service.query.ServiceQuery.QueryResult
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.{ExecutionContext, Future}

class ServiceQuerySpec extends FunSuite with Matchers with PatientScalaFutures {

  import QueryServiceTesting._
  import ServiceQuerySpec._

  import ExecutionContext.Implicits.global

  test("should invoke enricher by name") {
    whenReady(invokeCastService(4)) { r =>
      r.result shouldBe 4
    }
  }
  test("should invoke concat service") {
    whenReady(invokeConcatService("foo", "bar")) { r =>
      r.result shouldBe "foobar"
    }
  }
  test("should throw IllegalArgumentExcetion on negative argument") {
    assertThrows[IllegalArgumentException] {
      whenReady(invokeCastService(-1)) { _ =>
        ()
      }
    }
  }
  test("should throw exception on unexisting service") {
    assertThrows[ServiceQuery.ServiceNotFoundException] {
      whenReady(CreateQuery("cast", new CastIntToLongService).invoke("add")) { _ =>
        ()
      }
    }
  }

  private def invokeConcatService(s1: String, s2: String) =
    InvokeService(new ConcatService, "s1" -> s1, "s2" -> s2)

  private def invokeCastService(arg: Int) =
    InvokeService(new CastIntToLongService, "integer" -> arg)

}

object ServiceQuerySpec {

  class CastIntToLongService extends Service {
    @MethodToInvoke
    def cast(@ParamName("integer") n: Int)
            (implicit executionContext: ExecutionContext): Future[Long] = {
      n match {
        case negative if negative < 0 =>
          throw new IllegalArgumentException
        case _ => Future(n.toLong)
      }
    }
  }

  class ConcatService extends Service {
    @MethodToInvoke
    def concat(@ParamName("s1") s1: String, @ParamName("s2") s2: String)
              (implicit executionContext: ExecutionContext) =
      Future(s1 + s2)
  }

}

object QueryServiceTesting {

  object InvokeService {
    def apply(service: Service, args: (String, Any)*)
             (implicit executionContext: ExecutionContext): Future[QueryResult] = {
      CreateQuery("srv", service)
        .invoke("srv", args: _*)
    }
  }

  object CreateQuery{
    def apply(serviceName:String,service:Service)
             (implicit executionContext: ExecutionContext): ServiceQuery = {
      new ServiceQuery(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator {
        override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
          super.services(processObjectDependencies) ++ Map(serviceName -> WithCategories(service))
      }))
    }
  }

}