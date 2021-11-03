package pl.touk.nussknacker.engine.api.component

import cats.implicits.catsSyntaxSemigroup
import cats.kernel.Semigroup
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator, SimpleParameterEditor}

/**
  * This contains not only urls or icons but also parameter restrictions, used in e.g. validation
  * TODO: maybe icon/docs/componentGroup should be somehow separated as they are UI related?
  * TODO: componentId is work around for components duplication across multiple scenario types - we don't use it on ComponentsUiConfigExtractor
  */
@JsonCodec case class SingleComponentConfig(params: Option[Map[String, ParameterConfig]], icon: Option[String], docsUrl: Option[String], componentGroup: Option[ComponentGroupName], componentId: Option[ComponentId], disabled: Boolean = false) {
  def paramConfig(name: String): ParameterConfig = params.flatMap(_.get(name)).getOrElse(ParameterConfig.empty)
}

object SingleComponentConfig {

  val zero: SingleComponentConfig = SingleComponentConfig(None, None, None, None, None)

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
        x.componentGroup |+| y.componentGroup,
        y.componentId |+| x.componentId,
      )
    }
  }
}

@JsonCodec case class ParameterConfig(defaultValue: Option[String],
                                      editor: Option[ParameterEditor],
                                      validators: Option[List[ParameterValidator]],
                                      label: Option[String])

object ParameterConfig {
  val empty: ParameterConfig = ParameterConfig(None, None, None, None)
}

@JsonCodec case class AdditionalPropertyConfig(defaultValue: Option[String],
                                               editor: Option[SimpleParameterEditor],
                                               validators: Option[List[ParameterValidator]],
                                               label: Option[String])

object AdditionalPropertyConfig {
  val empty: AdditionalPropertyConfig = AdditionalPropertyConfig(None, None, None, None)
}
