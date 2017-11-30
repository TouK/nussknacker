package pl.touk.nussknacker.engine.api.process

case class WithCategories[+T](value: T, categories: List[String]) {
  def map[Y](f : T=>Y) = copy(value = f(value))
}

object WithCategories {
  def apply[T](value: T, categories: String*) : WithCategories[T] = WithCategories(value, categories.toList)
}
