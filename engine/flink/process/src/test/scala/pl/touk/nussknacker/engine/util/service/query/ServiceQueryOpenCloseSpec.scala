package pl.touk.nussknacker.engine.util.service.query

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.WithCategories
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.testing.{EmptyProcessConfigCreator, LocalModelData}
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class ServiceQueryOpenCloseSpec
  extends FlatSpec
    with Matchers
    with ScalaFutures
    with Eventually {

  import ServiceQueryOpenCloseSpec._

  private implicit val executionContext: ExecutionContextExecutor = ExecutionContext.Implicits.global

  it should "open and close service" in {
    val service = createService
    service.wasOpen shouldBe false
    whenReady(invokeService(4, service)) { r =>
      r.result shouldBe 4
    }
    service.wasOpen shouldBe true
    eventually {
      service.wasClose shouldBe true
    }
  }

  private def createService = {
    new CastIntToLongService with TimeMeasuringService
  }

  private def invokeService(arg: Int, service: Service) = {
    new ServiceQuery(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator {
      override def services(config: Config): Map[String, WithCategories[Service]] =
        super.services(config) ++ Map("cast" -> WithCategories(service, Nil))
    }))
      .invoke("cast", "integer" -> arg)
  }
}

object ServiceQueryOpenCloseSpec {

  abstract class CastIntToLongService extends Service with GenericTimeMeasuringService {
    val serviceName = "cast"
    var wasOpen = false
    var wasClose = false

    override def open(jobData: JobData): Unit = {
      wasOpen = true
      super.open(jobData)
    }

    override def close(): Unit = {
      super.close()
      wasClose = true
    }

    @MethodToInvoke
    def cast(@ParamName("integer") n: Int)
            (implicit executionContext: ExecutionContext,
             serviceInvocationCollector: ServiceInvocationCollector): Future[Long] = {
      serviceInvocationCollector.collect(s"invocation $n", Option(99L)) {
        measuring {
          n match {
            case negative if negative < 0 =>
              throw new IllegalArgumentException
            case _ => Future(n.toLong)
          }
        }
      }
    }
  }

}
