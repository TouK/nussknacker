package pl.touk.nussknacker.engine.util.service

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, SafeLazyMetrics}

import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait TimeMeasuringService { self: Service =>

  protected var timeMeasurement: AsyncExecutionTimeMeasurement = _

  override def open(context: EngineRuntimeContext): Unit = {
    timeMeasurement = new AsyncExecutionTimeMeasurement(context, serviceName, tags)
  }

  def measuring[T](actionFun: => Future[T])(implicit ec: ExecutionContext) : Future[T] = timeMeasurement.measuring(actionFun)

  protected def tags: Map[String, String] = Map()

  protected def serviceName: String

}

class AsyncExecutionTimeMeasurement(context: EngineRuntimeContext,
                                    serviceName: String,
                                    tags: Map[String, String],
                                    instantTimerWindow: Duration = 20 seconds) extends LazyLogging {

  protected def metricName: NonEmptyList[String] = NonEmptyList.of("service")

  //TODO: add metrics eagerly during open, so that we don't need this map
  private val metrics = new SafeLazyMetrics[String, EspTimer]

  def measuring[T](actionFun: => Future[T])(implicit ec: ExecutionContext) : Future[T] = {
    measuring(tags)(actionFun)
  }

  def measuring[T](tags: Map[String, String])(actionFun: => Future[T])(implicit ec: ExecutionContext) : Future[T] = {
    val start = System.nanoTime()
    val action = actionFun
    action.onComplete { result =>
      detectMeterName(result).foreach { meterName =>
        getOrCreateTimer(tags, meterName).update(start)
      }
    }
    action
  }

  protected def detectMeterName(result: Try[Any]) : Option[String] = result match {
    case Success(_) => Some("OK")
    case Failure(_) => Some("FAIL")
  }

  private def getOrCreateTimer(tags: Map[String, String], meterType: String) : EspTimer = {
    metrics.getOrCreate(meterType, () => espTimer(tags + ("serviceName" -> serviceName), metricName :+ meterType))
  }

  def espTimer(tags: Map[String, String], name: NonEmptyList[String]): EspTimer = {
    //TODO: so far in ServiceQuery we don't do open(...) because there's no RuntimeContext
    //we should make it nicer than below, but it's still better than throwing NullPointerException
    if (context == null) {
      logger.info("open not called on TimeMeasuringService - is it ServiceQuery? Using dummy timer")
      EspTimer(() => (), _ => {})
    } else {
      context.metricsProvider.espTimer(MetricIdentifier(name, tags), instantTimerWindow.toSeconds)
    }
  }

}
