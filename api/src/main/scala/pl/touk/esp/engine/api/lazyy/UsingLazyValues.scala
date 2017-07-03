package pl.touk.esp.engine.api.lazyy

import cats.data.State
import pl.touk.esp.engine.api.Context

import scala.language.implicitConversions

trait UsingLazyValues {

  protected def lazyValue[T](serviceId: String, params: (String, Any)*): State[ContextWithLazyValuesProvider, T] =
    State(_.lazyValue(serviceId, params))

}

case class ContextWithLazyValuesProvider(context: Context, lazyValuesProvider: LazyValuesProvider) {
  def lazyValue[T](serviceId: String, params: Seq[(String, Any)]): (ContextWithLazyValuesProvider, T) = {
    val (modifiedLazyContext, value) = lazyValuesProvider[T](context.lazyContext, serviceId, params)
    (copy(context = context.withLazyContext(modifiedLazyContext)), value)
  }
}
