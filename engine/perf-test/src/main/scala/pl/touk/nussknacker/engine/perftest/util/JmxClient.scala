package pl.touk.nussknacker.engine.perftest.util

import java.lang.management.{ManagementFactory, MemoryMXBean, MemoryUsage}
import javax.management.MBeanServerConnection
import javax.management.remote.{JMXConnectorFactory, JMXServiceURL}

import com.sun.management.OperatingSystemMXBean

import scala.concurrent._

class JmxClient(connection: MBeanServerConnection)
               (implicit ec: ExecutionContext) {

  private val memProxy = ManagementFactory.newPlatformMXBeanProxy(
    connection,
    ManagementFactory.MEMORY_MXBEAN_NAME,
    classOf[MemoryMXBean])

  private val systemProxy = ManagementFactory.newPlatformMXBeanProxy(
    connection,
    ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME,
    classOf[OperatingSystemMXBean])

  def memoryUsage(): Future[MemoryUsage] = {
    Future {
      blocking {
        memProxy.getHeapMemoryUsage
      }
    }
  }

  def cpuTime(): Future[Long] = {
    Future {
      blocking {
        systemProxy.getProcessCpuTime
      }
    }
  }

  def cpuLoad(): Future[Double] = {
    Future {
      blocking {
        systemProxy.getProcessCpuLoad
      }
    }
  }

}

object JmxClient {

  case class JmxConfig(host: String, port: Int)

  /**
    * Należy wywołać server z tymi parametrami:
    * -Dcom.sun.management.jmxremote
    * -Dcom.sun.management.jmxremote.port=port
    * -Dcom.sun.management.jmxremote.authenticate=false
    * -Dcom.sun.management.jmxremote.ssl=false
    * -Djava.rmi.server.hostname=host
    */
  def connect(config: JmxConfig)
             (implicit ec: ExecutionContext): JmxClient = {
    import config._
    connect(new JMXServiceURL(s"service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi"))
  }

  def connect(parsedUrl: JMXServiceURL)
             (implicit ec: ExecutionContext): JmxClient = {
    val connection = JMXConnectorFactory.connect(parsedUrl, null).getMBeanServerConnection
    new JmxClient(connection)
  }

}