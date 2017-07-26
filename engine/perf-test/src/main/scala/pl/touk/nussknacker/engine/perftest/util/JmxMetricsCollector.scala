package pl.touk.nussknacker.engine.perftest.util

import java.util.logging.{Level, Logger}

import akka.actor.ActorSystem
import org.slf4j.bridge.SLF4JBridgeHandler
import pl.touk.nussknacker.engine.perftest.util.JmxClient.JmxConfig
import pl.touk.nussknacker.engine.perftest.util.JmxMetricsCollector.StoppedMetricsCollector

import scala.concurrent.Await
import scala.concurrent.duration._

class JmxMetricsCollector(memoryCollector: StatisticsCollector[Long],
                          loadCollector: StatisticsCollector[Double]) {


  def collectIn[T](f: => T): (T, StoppedMetricsCollector) = {
    val started = start()
    val result = f
    (result, started.stop())
  }

  def start(): Started =
    new Started(memoryCollector.start(), loadCollector.start())

  class Started(startedMemoryCollector: memoryCollector.Started,
                startedLoadCollector: loadCollector.Started) {

    def stop(): StoppedMetricsCollector =
      StoppedMetricsCollector(
        startedMemoryCollector.stop().histogram,
        startedLoadCollector.stop().histogram
      )
  }

}

object JmxMetricsCollector {

  def apply(system: ActorSystem,
            interval: FiniteDuration,
            config: JmxConfig) = {
    import system.dispatcher
    val client = JmxClient.connect(config)
    val baseMemoryUsage = Await.result(client.memoryUsage(), 1 minute).getUsed
    val memoryCollector = StatisticsCollector(system, interval, "memory") {
      client.memoryUsage().map(_.getUsed - baseMemoryUsage)
    }
    val loadCollector = StatisticsCollector(system, interval, "load") {
      client.cpuLoad()
    }
    new JmxMetricsCollector(memoryCollector, loadCollector)
  }

  case class StoppedMetricsCollector(memoryHistogram: Histogram[Long],
                                     loadHistogram: Histogram[Double]) {

    def show =
      s"""memory: ${memoryHistogram.show}
         |load: ${loadHistogram.show}""".stripMargin

  }

}