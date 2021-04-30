package pl.touk.nussknacker.engine.util.service

import cats.data.NonEmptyList
import pl.touk.nussknacker.engine.util.metrics.RateMeter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait GenericTimeMeasuringService {

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

  private def getOrCreateTimer(tags: Map[String, String], meterType: String) : EspTimer = metrics.getOrElseUpdate(meterType,
    espTimer(tags + ("serviceName" -> serviceName), metricName :+ meterType))


  def espTimer(tags: Map[String, String], metricName: NonEmptyList[String]) : EspTimer

}
