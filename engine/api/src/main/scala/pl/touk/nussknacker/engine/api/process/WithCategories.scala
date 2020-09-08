package pl.touk.nussknacker.engine.api.process

import cats.kernel.Semigroup
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator, SimpleParameterEditor}

// todo: rename it? its no longer just a value with categories
case class WithCategories[+T](value: T, categories: List[String], nodeConfig: SingleNodeConfig) {
  def map[Y](f : T => Y): WithCategories[Y] = {
    copy(value = f(value))
  }

  def withNodeConfig(newNodeConfig: SingleNodeConfig): WithCategories[T] = {
    copy(nodeConfig = newNodeConfig)
  }
}

object WithCategories {
  def apply[T](value: T, categories: String*): WithCategories[T] = {
    WithCategories(value, categories.toList, SingleNodeConfig.zero)
  }
}

/**
  * This contains not only urls or icons but also parameter restrictions, used in e.g. validation
  * TODO: maybe icon/docs/category should be somehow separated as they are UI related?
  */
@JsonCodec case class SingleNodeConfig(params: Option[Map[String, ParameterConfig]], icon: Option[String], docsUrl: Option[String], category: Option[String]) {
  def paramConfig(name: String): ParameterConfig = params.flatMap(_.get(name)).getOrElse(ParameterConfig.empty)
}

object ParameterConfig {
  val empty: ParameterConfig = ParameterConfig(None, None, None, None)

  implicit val semigroup: Semigroup[ParameterConfig] = {
    import cats.syntax.semigroup._
    import SemiGroupImplicits._

    Semigroup.instance[ParameterConfig] { (x, y) =>
      ParameterConfig(
        x.defaultValue |+| y.defaultValue,
        x.editor |+| y.editor,
        x.validators |+| y.validators,
        x.label |+| y.label
      )
    }
  }
}


@JsonCodec case class ParameterConfig(defaultValue: Option[String],
                                      editor: Option[ParameterEditor],
                                      validators: Option[List[ParameterValidator]],
                                      label: Option[String]) extends WithSemiGroup[ParameterConfig] {
  override def semigroup: Semigroup[ParameterConfig] = implicitly[Semigroup[ParameterConfig]]
}

@JsonCodec case class AdditionalPropertyConfig(defaultValue: Option[String],
                                               editor: Option[SimpleParameterEditor],
                                               validators: Option[List[ParameterValidator]],
                                               label: Option[String])

object AdditionalPropertyConfig {
  val empty: AdditionalPropertyConfig = AdditionalPropertyConfig(None, None, None, None)
}

object SingleNodeConfig {
  import cats.syntax.semigroup._

  val zero = SingleNodeConfig(None, None, None, None)

  implicit val semigroup: Semigroup[SingleNodeConfig] = {
    import SemiGroupImplicits._
    Semigroup.instance[SingleNodeConfig] { (x, y) =>
      SingleNodeConfig(
        x.params |+| y.params,
        x.icon |+| y.icon,
        x.docsUrl |+| y.docsUrl,
        x.category |+| y.category
      )
    }
  }
}

trait WithSemiGroup[T] { self: T =>
  def semigroup: Semigroup[T]
}

private object SemiGroupImplicits {
  import cats.syntax.semigroup._

  implicit def takeFirstIfNoSemi[T]: Semigroup[T] = Semigroup.instance[T] {
    case (a:WithSemiGroup[T]@unchecked, b:WithSemiGroup[T]@unchecked) => a.semigroup.combine(a.asInstanceOf[T],b.asInstanceOf[T])
    case (a, _) => a
  }

  implicit def takeLeftOptionSemi[T: Semigroup]: Semigroup[Option[T]] = Semigroup.instance[Option[T]] {
    case (None, None) => None
    case (None, Some(x)) => Some(x)
    case (Some(x), Some(y)) => Some(x |+| y)
    case (Some(x), _) => Some(x)
  }

  import cats.instances.list._
  implicit def mergeOptionListSemi[T]: Semigroup[Option[List[T]]] = Semigroup.instance[Option[List[T]]] {
    case (None, None) => None
    case (Some(x), None) => Some(x)
    case (None, Some(x)) => Some(x)
    case (Some(x), Some(y))  => Some(x |+| y)
  }
  implicit def takeLeftMapSemi[K, V: Semigroup]: Semigroup[Map[K, V]] = Semigroup.instance[Map[K, V]] { (x, y) =>
    val keys = x.keys ++ y.keys
    keys.map { k =>
      k -> (x.get(k) |+| y.get(k)).get
    }.toMap
  }

  implicit def naturalOptionMapSemi[K, V: Semigroup]: Semigroup[Option[Map[K, V]]] = Semigroup.instance[Option[Map[K, V]]] {
    case (None, None) => None
    case (Some(x), None) => Some(x)
    case (None, Some(x)) => Some(x)
    case (Some(x), Some(y)) => Some(x |+| y)
  }
}