package pl.touk.nussknacker.ui.process.periodic

trait PeriodicProcessesManagerProvider {

  def provide(
      processingType: String,
  ): PeriodicProcessesManager

}
