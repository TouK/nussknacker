package pl.touk.nussknacker.engine.api.runtimecontext

trait EngineRuntimeContextLifecycle {

  def open(context: EngineRuntimeContext): Unit = {}

}
