package pl.touk.esp.engine.api.lazyy

import cats.effect.IO

trait LazyValuesProvider {

  def apply[T](context: LazyContext, serviceId: String, params: Seq[(String, Any)]): IO[(LazyContext, T)]

}