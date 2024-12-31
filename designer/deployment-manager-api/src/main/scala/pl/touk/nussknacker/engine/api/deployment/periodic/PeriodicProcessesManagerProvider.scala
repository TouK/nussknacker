package pl.touk.nussknacker.engine.api.deployment.periodic

trait PeriodicProcessesManagerProvider {

  def provide(
      processingType: String,
  ): PeriodicProcessesManager

}

object NoOpPeriodicProcessesManagerProvider extends PeriodicProcessesManagerProvider {

  override def provide(
      processingType: String
  ): PeriodicProcessesManager = NoOpPeriodicProcessesManager

}
