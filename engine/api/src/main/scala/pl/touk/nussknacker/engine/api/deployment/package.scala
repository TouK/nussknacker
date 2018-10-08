package pl.touk.nussknacker.engine.api

package object deployment {
  // e. g. JobId for Flink jobs; ProcessName for Standalone jobs
  final case class DeploymentId(value: String) extends AnyVal
}
