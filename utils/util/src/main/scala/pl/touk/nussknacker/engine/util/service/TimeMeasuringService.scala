package pl.touk.nussknacker.engine.util.service

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.Service
import pl.touk.nussknacker.engine.api.runtimecontext.EngineRuntimeContext
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, SafeLazyMetrics}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait TimeMeasuringService extends LazyLogging { self: Service =>

  var context: EngineRuntimeContext = _

  override def open(runtimeContext: EngineRuntimeContext): Unit = {
    context = runtimeContext
  }

  protected def metricName: NonEmptyList[String] = NonEmptyList.of("service")

  //TODO: add metrics eagerly during open, so that we don't need this map
  private val metrics = new SafeLazyMetrics[String, EspTimer]

  protected def measuring[T](actionFun: => Future[T])(implicit ec: ExecutionContext) : Future[T] = {
    measuring(tags)(actionFun)
  }

  protected def measuring[T](tags: Map[String, String])(actionFun: => Future[T])(implicit ec: ExecutionContext) : Future[T] = {
    val start = System.nanoTime()
    val action = actionFun
    action.onComplete { result =>
      detectMeterName(result).foreach { meterName =>
        getOrCreateTimer(tags, meterName).update(start)
      }
    }
    action
  }

  protected def tags: Map[String, String] = Map()

  protected def serviceName: String

  protected def instantTimerWindowInSeconds : Long = 20

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
      context.metricsProvider.espTimer(MetricIdentifier(name, tags), instantTimerWindowInSeconds)
    }
  }

}
