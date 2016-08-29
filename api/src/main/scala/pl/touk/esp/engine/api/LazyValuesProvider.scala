package pl.touk.esp.engine.api

trait LazyValuesProvider {

  def apply[T](serviceId: String, params: (String, Any)*): ValueWithModifiedContext[T]

}