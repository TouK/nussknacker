package pl.touk.nussknacker.engine.api

case class ValueWithContext[T](value: T, context: ScenarioProcessingContext) {

  def map[N](f: T => N): ValueWithContext[N] =
    copy(value = f(value))

}
