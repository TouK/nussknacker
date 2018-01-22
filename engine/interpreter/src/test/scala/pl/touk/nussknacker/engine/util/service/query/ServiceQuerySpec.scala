package pl.touk.nussknacker.engine.util.service.query


import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}

import scala.concurrent.{ExecutionContext, Future}

class ServiceQuerySpec
  extends FlatSpec
    with Matchers
    with ScalaFutures {

  import ServiceQuerySpec._

  import ExecutionContext.Implicits.global
  import ServiceQuery.Implicits.metaData

  it should "invoke enricher by name" in {
    whenReady(invokeCastService(4)) { r =>
      r shouldBe 4
    }
  }
  it should "invoke concat service" in {
    whenReady(invokeConcatService("foo", "bar")) { r =>
      r shouldBe "foobar"
    }
  }
  it should "throw IllegalArgumentExcetion on negative argument" in {
    assertThrows[IllegalArgumentException] {
      whenReady(invokeCastService(-1)) { _ =>
        ()
      }
    }
  }
  it should "throw exception on unexisting service" in {
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
  object InvokeService{
    def apply(service: Service, args: (String, Any)*)
             (implicit executionContext: ExecutionContext, metaData: MetaData): Future[Any] = {
      CreateQuery("srv", service)
        .invoke("srv", args:_*)
    }
  }
  object CreateQuery{
    def apply(serviceName:String,service:Service)
             (implicit executionContext: ExecutionContext, metaData: MetaData): ServiceQuery = {
      new ServiceQuery(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator {
        override def services(config: Config): Map[String, WithCategories[Service]] =
          super.services(config) ++ Map(serviceName -> WithCategories(service))
      }))
    }
  }

  class ConcatService extends Service {
    @MethodToInvoke
    def concat(@ParamName("s1") s1: String, @ParamName("s2") s2: String)
              (implicit executionContext: ExecutionContext) =
      Future(s1 + s2)
  }

}


