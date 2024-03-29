package pl.touk.nussknacker.engine.flink.test

import com.github.ghik.silencer.silent
import org.apache.flink.configuration._

object FlinkTestConfiguration {

  // better to create each time because is mutable
  @silent("deprecated") def configuration(taskManagersCount: Int = 2, taskSlotsCount: Int = 8): Configuration = {
    import scala.collection.JavaConverters._

    val config = new Configuration
    config.setInteger(ConfigConstants.LOCAL_NUMBER_TASK_MANAGER, taskManagersCount)
    config.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, taskSlotsCount)
    // to prevent OutOfMemoryError: Could not allocate enough memory segments for NetworkBufferPool on low memory env (like Travis)
    config.set(TaskManagerOptions.NETWORK_MEMORY_MIN, MemorySize.parse("16m"))
    config.set(TaskManagerOptions.NETWORK_MEMORY_MAX, MemorySize.parse("16m"))

    // and this is a work around for a behaviour added in https://issues.apache.org/jira/browse/FLINK-32265
    // see details in pl.touk.nussknacker.engine.flink.test.MiniClusterExecutionEnvironment#execute
    config.set(PipelineOptions.CLASSPATHS, List("http://dummy-classpath.invalid").asJava)
  }

}
