package pl.touk.esp.engine.api.lazyy

import pl.touk.esp.engine.api.Context

trait UsingLazyValues {

  protected def lazyValue[T](serviceId: String, params: (String, Any)*): ContextWithLazyValuesProvider => (ContextWithLazyValuesProvider, T) =
    _.lazyValue(serviceId, params)

  protected implicit class LazyValuesFunction[T](f: ContextWithLazyValuesProvider => (ContextWithLazyValuesProvider, T)) {

    def map[N](f2: T => N): (ContextWithLazyValuesProvider) => (ContextWithLazyValuesProvider, N) = {
      f andThen {
        case (ctxLvp, v) =>
          val nv = f2(v)
          (ctxLvp, nv)
      }
    }

    def flatMap[N](f2: T => (ContextWithLazyValuesProvider => (ContextWithLazyValuesProvider, N))): ContextWithLazyValuesProvider => (ContextWithLazyValuesProvider, N) = {
      f andThen {
        case (ctxLvp, v) =>
          val f3 = f2(v)
          f3(ctxLvp)
      }
    }

  }

}

case class ContextWithLazyValuesProvider(context: Context, lazyValuesProvider: LazyValuesProvider) {
  def lazyValue[T](serviceId: String, params: Seq[(String, Any)]): (ContextWithLazyValuesProvider, T) = {
    val (modifiedLazyContext, value) = lazyValuesProvider[T](context.lazyContext, serviceId, params)
    (copy(context = context.withLazyContext(modifiedLazyContext)), value)
  }
}
