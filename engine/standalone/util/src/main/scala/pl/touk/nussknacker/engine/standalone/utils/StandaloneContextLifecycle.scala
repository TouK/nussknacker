package pl.touk.nussknacker.engine.standalone.utils

trait StandaloneContextLifecycle {

  def open(context: StandaloneContext) : Unit

  def close() : Unit


}
