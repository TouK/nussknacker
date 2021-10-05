package pl.touk.nussknacker.engine.api.process

import cats.kernel.Semigroup
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentGroupName
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

/**
  * This contains not only urls or icons but also parameter restrictions, used in e.g. validation
  * TODO: maybe icon/docs/componentGroup should be somehow separated as they are UI related?
  * TODO: Move it to component package?
  */
@JsonCodec case class SingleComponentConfig(params: Option[Map[String, ParameterConfig]], icon: Option[String], docsUrl: Option[String], componentGroup: Option[ComponentGroupName], disabled: Boolean = false) {
  def paramConfig(name: String): ParameterConfig = params.flatMap(_.get(name)).getOrElse(ParameterConfig.empty)
}

object ParameterConfig {
  val empty: ParameterConfig = ParameterConfig(None, None, None, None)
}

@JsonCodec case class ParameterConfig(defaultValue: Option[String],
                                      editor: Option[ParameterEditor],
                                      validators: Option[List[ParameterValidator]],
                                      label: Option[String])

@JsonCodec case class AdditionalPropertyConfig(defaultValue: Option[String],
                                               editor: Option[SimpleParameterEditor],
                                               validators: Option[List[ParameterValidator]],
                                               label: Option[String])

object AdditionalPropertyConfig {
  val empty: AdditionalPropertyConfig = AdditionalPropertyConfig(None, None, None, None)
}

object SingleComponentConfig {
  import cats.syntax.semigroup._

  val zero = SingleComponentConfig(None, None, None, None)

  implicit val semigroup: Semigroup[SingleComponentConfig] = {
    implicit def takeLeftOptionSemi[T]: Semigroup[Option[T]] = Semigroup.instance[Option[T]] {
      case (None, None) => None
      case (None, Some(x)) => Some(x)
      case (Some(x), _) => Some(x)
    }

    implicit def takeLeftMapSemi[K, V]: Semigroup[Map[K, V]] = Semigroup.instance[Map[K, V]] { (x, y) =>
      val keys = x.keys ++ y.keys
      keys.map { k =>
        k -> (x.get(k) |+| y.get(k)).get
      }.toMap
    }

    implicit def naturalOptionMapSemi[K, V]: Semigroup[Option[Map[K, V]]] = Semigroup.instance[Option[Map[K, V]]] {
      case (None, None) => None
      case (Some(x), None) => Some(x)
      case (None, Some(x)) => Some(x)
      case (Some(x), Some(y)) => Some(x |+| y)
    }

    Semigroup.instance[SingleComponentConfig] { (x, y) =>
      SingleComponentConfig(
        x.params |+| y.params,
        x.icon |+| y.icon,
        x.docsUrl |+| y.docsUrl,
        x.componentGroup |+| y.componentGroup
      )
    }
  }
}
