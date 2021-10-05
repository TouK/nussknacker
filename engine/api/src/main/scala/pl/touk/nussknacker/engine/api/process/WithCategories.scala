package pl.touk.nussknacker.engine.api.process

import cats.kernel.Semigroup
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.{ComponentGroupName, SingleComponentConfig}
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator, SimpleParameterEditor}

// todo: rename it? its no longer just a value with categories
case class WithCategories[+T](value: T, categories: List[String], componentConfig: SingleComponentConfig) {
  def map[Y](f : T => Y): WithCategories[Y] = {
    copy(value = f(value))
  }

  def withComponentConfig(newComponentConfig: SingleComponentConfig): WithCategories[T] = {
    copy(componentConfig = newComponentConfig)
  }
}

object WithCategories {
  def apply[T](value: T, categories: String*): WithCategories[T] = {
    WithCategories(value, categories.toList, SingleComponentConfig.zero)
  }
}

