package pl.touk.esp.engine.perftest.util

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import concurrent.duration._
import scala.concurrent.Future

class StatisticsCollectorTest extends FlatSpec with ScalaFutures with Matchers with BeforeAndAfterAll {

  private var system: ActorSystem = _

  it should "collect statistics" in {
    val i = new AtomicInteger(1)
    val interval = 500 millis
    val count = 1
    val collector = StatisticsCollector[Int](system, interval, "foo") {
      Future.successful(i.getAndIncrement())
    }
    val started = collector.start()
    Thread.sleep((interval * (count + 0.9)).toMillis)
    val stopped = started.stop()
    println(stopped.histogram)
    stopped.histogram.mean shouldEqual 1
  }

  override protected def beforeAll(): Unit = {
    system = ActorSystem("StatisticsCollectorTest")
  }

  override protected def afterAll(): Unit = {
    system.terminate()
  }
}
