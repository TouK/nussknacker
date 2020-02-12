package pl.touk.nussknacker.engine.demo.service

import java.io.File

import org.apache.commons.io.FileUtils
import pl.touk.nussknacker.engine.api.editor.{LabeledExpression, SimpleEditor, SimpleEditorType}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.{MethodToInvoke, ParamName, Service}
import pl.touk.nussknacker.engine.flink.util.service.TimeMeasuringService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class AlertService(alertFile: String) extends Service with TimeMeasuringService  {

  @MethodToInvoke
  def invoke(@ParamName("message") message: String,
             @ParamName("category")
             @SimpleEditor(
               `type` = SimpleEditorType.FIXED_VALUES_EDITOR,
               possibleValues = Array(
                 new LabeledExpression(expression = "'warning'", label = "warning"),
                 new LabeledExpression(expression = "'alert'", label = "alert")
               )
             )
             category: String)
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
