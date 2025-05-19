package pl.touk.nussknacker.engine.api.component

trait ComponentLifecycle { self: Component =>
  def closeComponent(): Unit = {}
}
