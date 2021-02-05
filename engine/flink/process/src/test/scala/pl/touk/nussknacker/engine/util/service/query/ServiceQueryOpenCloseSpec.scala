package pl.touk.nussknacker.engine.util.service.query

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{ProcessObjectDependencies, WithCategories}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.util.service.GenericTimeMeasuringService
import pl.touk.nussknacker.test.PatientScalaFutures

import scala.concurrent.{ExecutionContext, Future}

class ServiceQueryOpenCloseSpec
  extends FunSuite
    with Matchers
    with PatientScalaFutures {

  import ServiceQueryOpenCloseSpec._

  private implicit val executionContext: ExecutionContext = ExecutionContext.Implicits.global

  test("open and close service") {
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

  test("should be able to invoke multiple times using same config") {
    val modelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        super.services(processObjectDependencies) ++ Map("cast" -> WithCategories(createService))
    })

    whenReady(new ServiceQuery(modelData).invoke("cast", "integer" -> 4)) {
      _.result shouldBe 4
    }
    whenReady(new ServiceQuery(modelData).invoke("cast", "integer" -> 5)) {
      _.result shouldBe 5
    }
  }

  private def createService = {
    new CastIntToLongService with TimeMeasuringService
  }

  private def invokeService(arg: Int, service: Service) = {
    new ServiceQuery(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator {
      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
        super.services(processObjectDependencies) ++ Map("cast" -> WithCategories(service))
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
      if (wasClose) {
        throw new IllegalStateException("Closed...")
      }
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
