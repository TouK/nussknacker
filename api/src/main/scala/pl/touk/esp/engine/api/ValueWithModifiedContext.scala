package pl.touk.esp.engine.api

case class ValueWithModifiedContext[T](value: T, context: Context) {

  def map[N](f: T => N): ValueWithModifiedContext[N] =
    copy(value = f(value))

}
