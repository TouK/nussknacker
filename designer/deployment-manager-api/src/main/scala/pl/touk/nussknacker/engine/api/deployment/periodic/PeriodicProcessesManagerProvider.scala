package pl.touk.nussknacker.engine.api.deployment.periodic

trait PeriodicProcessesManagerProvider {

  def provide(
      deploymentManagerName: String,
      processingType: String,
  ): PeriodicProcessesManager

}

object NoOpPeriodicProcessesManagerProvider extends PeriodicProcessesManagerProvider {

  override def provide(
      deploymentManagerName: String,
      processingType: String
  ): PeriodicProcessesManager = NoOpPeriodicProcessesManager

}
