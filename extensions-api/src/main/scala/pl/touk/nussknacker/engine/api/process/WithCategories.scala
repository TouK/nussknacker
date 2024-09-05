package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.component.{ComponentConfig, DesignerWideComponentId}

// TODO: This is deprecated API, remove it after ConfiCreator API will be removed
case class WithCategories[+T](value: T, categories: Option[List[String]], componentConfig: ComponentConfig) {

  def map[Y](f: T => Y): WithCategories[Y] = {
    copy(value = f(value))
  }

  def withComponentConfig(newComponentConfig: ComponentConfig): WithCategories[T] = {
    copy(componentConfig = newComponentConfig)
  }

  def withComponentId(componentId: Option[String]): WithCategories[T] =
    componentId
      .map(DesignerWideComponentId.apply)
      .map(id => withComponentConfig(componentConfig.copy(componentId = Some(id))))
      .getOrElse(this)

}

object WithCategories {

  def apply[T](value: T, category: String, categories: String*): WithCategories[T] = {
    WithCategories(value, Some(category :: categories.toList), ComponentConfig.zero)
  }

  def anyCategory[T](value: T): WithCategories[T] = {
    WithCategories(value, None, ComponentConfig.zero)
  }

}
