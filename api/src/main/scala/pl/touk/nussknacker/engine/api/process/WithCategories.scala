package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.component.SingleComponentConfig

// todo: rename it? its no longer just a value with categories
case class WithCategories[+T](value: T, categories: Option[List[String]], componentConfig: SingleComponentConfig) {
  def map[Y](f : T => Y): WithCategories[Y] = {
    copy(value = f(value))
  }

  def withComponentConfig(newComponentConfig: SingleComponentConfig): WithCategories[T] = {
    copy(componentConfig = newComponentConfig)
  }
}

object WithCategories {
  def apply[T](value: T, categories: String*): WithCategories[T] = {
    WithCategories(value, Some(categories.toList), SingleComponentConfig.zero)
  }
}
