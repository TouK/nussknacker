package pl.touk.nussknacker.engine.util.service

import pl.touk.nussknacker.engine.util.metrics.RateMeter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait GenericTimeMeasuringService {

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

  private def getOrCreateTimer(tags: Map[String, String], name: String) : EspTimer = metrics.getOrElseUpdate(name,
    espTimer(tags + ("serviceName" -> serviceName), name))


  def espTimer(tags: Map[String, String], name: String) : EspTimer

}

case class EspTimer(rateMeter: RateMeter, histogram: Long => Unit) {

  def update(nanoTimeStart: Long): Unit = {
    val delta = System.nanoTime() - nanoTimeStart
    rateMeter.mark()
    histogram.apply(delta)
  }
}
