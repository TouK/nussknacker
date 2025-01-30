package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.MetaDataExtractor

class StreamExecutionEnvironmentWithParallelismOverride private (
    val streamExecutionEnv: StreamExecutionEnvironment,
    parallelismOverride: Option[Int]
) {

  def overrideParallelismIfNeeded(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    parallelismOverride.map { maxParallelism =>
      val scenarioParallelism = MetaDataExtractor
        .extractTypeSpecificDataOrDefault[StreamMetaData](canonicalProcess.metaData, StreamMetaData())
        .parallelism
      if (scenarioParallelism.exists(_ > maxParallelism)) {
        canonicalProcess.copy(metaData =
          canonicalProcess.metaData.copy(additionalFields =
            canonicalProcess.metaData.additionalFields.copy(properties =
              canonicalProcess.metaData.additionalFields.properties + (StreamMetaData.parallelismName -> maxParallelism.toString)
            )
          )
        )
      } else {
        canonicalProcess
      }
    } getOrElse canonicalProcess
  }

}

object StreamExecutionEnvironmentWithParallelismOverride {

  def apply(env: StreamExecutionEnvironment, maxParallelism: Int): StreamExecutionEnvironmentWithParallelismOverride =
    new StreamExecutionEnvironmentWithParallelismOverride(env, Some(maxParallelism))

  def withoutParallelismOverriding(env: StreamExecutionEnvironment) =
    new StreamExecutionEnvironmentWithParallelismOverride(env, None)

}
