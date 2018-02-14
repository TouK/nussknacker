package pl.touk.nussknacker.engine.example.service

import java.io.File

import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, PossibleValues, Service}
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class AlertService(alertFile: String) extends Service with TimeMeasuringService  {

  @MethodToInvoke
  def invoke(@ParamName("message") message: String, @ParamName("category") @PossibleValues(Array("warning", "alert")) category: String)
            (implicit ec: ExecutionContext, collector: ServiceInvocationCollector) : Future[Unit] = {

    measuring {
      Thread.sleep(Random.nextInt(10))

      val messageWithCategory = s"$category: $message"
      collector.collect(messageWithCategory, Option(())) {
        Future {
          FileUtils.writeStringToFile(new File(alertFile), messageWithCategory, true)
        }
      }
    }
  }

  override protected def serviceName: String = "alertService"
}
