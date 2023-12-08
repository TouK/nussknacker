package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.component.{ComponentId, SingleComponentConfig}

// TODO: Remove categories when ConfigCreator will be removed. After that rename it to WithConfig and move out of API module
case class WithCategories[+T](value: T, categories: Option[List[String]], componentConfig: SingleComponentConfig) {

  def map[Y](f: T => Y): WithCategories[Y] = {
    copy(value = f(value))
  }

  def withComponentConfig(newComponentConfig: SingleComponentConfig): WithCategories[T] = {
    copy(componentConfig = newComponentConfig)
  }

  def withComponentId(componentId: Option[String]): WithCategories[T] =
    componentId
      .map(ComponentId.apply)
      .map(id => withComponentConfig(componentConfig.copy(componentId = Some(id))))
      .getOrElse(this)

}

object WithCategories {

  def apply[T](value: T, category: String, categories: String*): WithCategories[T] = {
    WithCategories(value, Some(category :: categories.toList), SingleComponentConfig.zero)
  }

  def anyCategory[T](value: T): WithCategories[T] = {
    WithCategories(value, None, SingleComponentConfig.zero)
  }

}
