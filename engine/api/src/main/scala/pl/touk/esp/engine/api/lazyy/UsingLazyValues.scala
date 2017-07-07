package pl.touk.esp.engine.api.lazyy

import cats.data.StateT
import cats.effect.IO

trait UsingLazyValues {

  type LazyState[T] = StateT[IO, ContextWithLazyValuesProvider, T]

  protected def lazyValue[T](serviceId: String, params: (String, Any)*): LazyState[T]
    = StateT(_.lazyValue(serviceId, params))
}

case class ContextWithLazyValuesProvider(context: LazyContext, lazyValuesProvider: LazyValuesProvider)  {

  def lazyValue[T](serviceId: String, params: Seq[(String, Any)]): IO[(ContextWithLazyValuesProvider, T)] = {
    lazyValuesProvider[T](context, serviceId, params).map { case (modifiedLazyContext, value) =>
      (copy(context = modifiedLazyContext), value)
    }
  }
}