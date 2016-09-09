package pl.touk.esp.engine.api.lazyy

trait LazyValuesProvider {

  def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]): (LazyContext, T)

}