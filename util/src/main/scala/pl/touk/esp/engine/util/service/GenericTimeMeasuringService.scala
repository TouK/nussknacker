package pl.touk.esp.engine.util.service

import pl.touk.esp.engine.util.metrics.GenericInstantRateMeter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait GenericTimeMeasuringService {

  @transient lazy val metrics : collection.concurrent.TrieMap[String, EspTimer] = collection.concurrent.TrieMap()

  protected def measuring[T](actionFun: => Future[T])(implicit ec: ExecutionContext) : Future[T] = {
    val start = System.nanoTime()
    val action = actionFun
    action.onComplete { result =>
      detectMeterName(result).foreach { meterName =>
        getOrCreateTimer(meterName).update(start)
      }
    }
    action
  }

  protected def serviceName: String

  protected def instantTimerWindowInSeconds : Long = 20

  protected def detectMeterName(result: Try[Any]) : Option[String] = result match {
    case Success(_) => Some("OK")
    case Failure(_) => Some("FAIL")
  }

  private def getOrCreateTimer(name: String) : EspTimer = metrics.getOrElseUpdate(name, espTimer(name))


  def espTimer(name: String) : EspTimer

}

case class EspTimer(rateMeter: GenericInstantRateMeter, histogram: Long => Unit) {

  def update(nanoTimeStart: Long) = {
    val delta = System.nanoTime() - nanoTimeStart
    rateMeter.mark()
    histogram.apply(delta)
  }
}
