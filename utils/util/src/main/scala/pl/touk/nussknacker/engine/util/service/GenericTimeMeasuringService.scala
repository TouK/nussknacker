package pl.touk.nussknacker.engine.util.service

import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.runtimecontext.{EngineRuntimeContext, EngineRuntimeContextLifecycle}
import pl.touk.nussknacker.engine.api.{JobData, Service}
import pl.touk.nussknacker.engine.util.metrics.MetricIdentifier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait GenericTimeMeasuringService extends EngineRuntimeContextLifecycle with LazyLogging { self: Service =>

  var context: EngineRuntimeContext = _

  override def open(jobData: JobData, runtimeContext: EngineRuntimeContext): Unit = {
    self.open(jobData)
    context = runtimeContext
  }

  protected def metricName: NonEmptyList[String] = NonEmptyList.of("service")

  @transient lazy val metrics : collection.concurrent.TrieMap[String, EspTimer] = collection.concurrent.TrieMap()

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
    //TrieMap.getOrElseUpdate alone is not enough, as e.g. in Flink "espTimer" can be invoked only once - otherwise
    //Metric may be already registered, which results in refusal to register metric without feedback. In such case
    //we can end up using not-registered metric.
    //The first check is for optimization purposes - to synchronize only at the beginnning
    metrics.get(meterType) match {
      case Some(value) => value
      case None => synchronized {
        metrics.getOrElseUpdate(meterType, espTimer(tags + ("serviceName" -> serviceName), metricName :+ meterType))
      }
    }
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
