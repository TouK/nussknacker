package pl.touk.nussknacker.engine.management.periodic

import io.circe.generic.JsonCodec
import org.apache.flink.api.common.ExecutionConfig
import pl.touk.nussknacker.engine.management.FlinkStreamingRestManager

private[periodic] object PeriodicFlinkRestModel {
  @JsonCodec(encodeOnly = true) case class DeployProcessRequest(entryClass: String = FlinkStreamingRestManager.MainClassName,
                                                                parallelism: Int = ExecutionConfig.PARALLELISM_DEFAULT,
                                                                programArgsList: List[String],
                                                                allowNonRestoredState: Boolean = true)

  @JsonCodec(decodeOnly = true) case class JarsResponse(files: Option[List[JarFile]])

  @JsonCodec(decodeOnly = true) case class JarFile(id: String, name: String)
}

