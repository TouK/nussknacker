package pl.touk.nussknacker.engine.api

/**
  * In some places we want to load all providers with ServiceLoader mechanism and choose one by name (taken from config)
  * @see ScalaServiceLoader.loadNamed
  */
trait NamedServiceProvider {

  def name: String

}
