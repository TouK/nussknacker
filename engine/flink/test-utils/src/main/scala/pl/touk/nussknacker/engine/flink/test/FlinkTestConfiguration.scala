package pl.touk.nussknacker.engine.flink.test

import com.github.ghik.silencer.silent
import org.apache.flink.configuration._

import java.net.URL

object FlinkTestConfiguration {

  // better to create each time because is mutable
  @silent("deprecated") def configuration(taskManagersCount: Int = 2, taskSlotsCount: Int = 8): Configuration = {
    import scala.collection.JavaConverters._

    val config = new Configuration
    config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, taskManagersCount)
    config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, taskSlotsCount)

    config.set(PipelineOptions.CLASSPATHS, classpathWorkaround.asJava)

    setupMemory(config)
  }

  // FIXME: better describe which classpath is used in this case
  // This is a work around for a behaviour added in https://issues.apache.org/jira/browse/FLINK-32265
  // Flink overwrite user classloader by the AppClassLoader if classpaths parameter is empty
  // (implementation in org.apache.flink.runtime.execution.librarycache.BlobLibraryCacheManager)
  // which holds all needed jars/classes in case of running from Scala plugin in IDE.
  // but in case of running from sbt it contains only sbt-launcher.jar
  def classpathWorkaround: List[String] = List("http://dummy-classpath.invalid")

  def setupMemory(config: Configuration): Configuration = {
    // to prevent OutOfMemoryError: Could not allocate enough memory segments for NetworkBufferPool on low memory env (like Travis)
    config.set(TaskManagerOptions.NETWORK_MEMORY_MIN, MemorySize.parse("16m"))
    config.set(TaskManagerOptions.NETWORK_MEMORY_MAX, MemorySize.parse("16m"))
    // This is to prevent memory problem in tests with mutliple Table API based aggregations. An IllegalArgExceptionon
    // is thrown with message "The minBucketMemorySize is not valid!" in
    // org.apache.flink.table.runtime.util.collections.binary.AbstractBytesHashMap.java:121 where memorySize is set
    // inside code-generated operator (like LocalHashAggregateWithKeys).
    config.set(TaskManagerOptions.MANAGED_MEMORY_SIZE, MemorySize.parse("100m"))
  }

}
