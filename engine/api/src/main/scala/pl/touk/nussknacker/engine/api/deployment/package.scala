package pl.touk.nussknacker.engine.api

import io.circe.generic.JsonCodec

package object deployment {
  // e. g. JobId for Flink jobs; ProcessName for Standalone jobs
  @JsonCodec case class DeploymentId(value: String) extends AnyVal
}
