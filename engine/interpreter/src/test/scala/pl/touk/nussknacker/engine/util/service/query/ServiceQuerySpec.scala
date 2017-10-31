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

  import  ExecutionContext.Implicits.global
  import  ServiceQuery.Implicits.metaData
  it should "invoke enricher by name" in {
    whenReady(invokeService(4, createService)) { r =>
      r shouldBe 4
    }
  }
  it should "throw IllegalArgumentExcetion on negative argument" in {
    assertThrows[IllegalArgumentException] {
      whenReady(invokeService(-1, createService)) { _ =>
        ()
      }
    }
  }
  it should "throw exception on unexisting service" in {
    assertThrows[ServiceQuery.ServiceNotFoundException] {
      whenReady(createQuery("cast", createService).invoke("add", Map())) { _ =>
        ()
      }
    }
  }

  private def createService = {
    new CastIntToLongService
  }

  private def invokeService(arg: Int, service: Service) = {
    createQuery("cast", service)
      .invoke("cast", Map("integer" -> arg))
  }

  private def createQuery(serviceName: String, service: Service) = {
    new ServiceQuery(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator {
      override def services(config: Config): Map[String, WithCategories[Service]] =
        super.services(config) ++ Map(serviceName -> WithCategories(service, Nil))
    }))
  }
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

}


