package pl.touk.esp.engine.api

object ValueWithContext {

  def apply(ir: InterpretationResult) : ValueWithContext[Any] = ValueWithContext(ir.output, ir.finalContext)
}

case class ValueWithContext[T](value: T, context: Context) {

  def map[N](f: T => N): ValueWithContext[N] =
    copy(value = f(value))

}
