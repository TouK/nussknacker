package pl.touk.nussknacker.engine.api.component

import cats.implicits.catsSyntaxSemigroup
import cats.kernel.Semigroup
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.definition.{ParameterEditor, ParameterValidator, SimpleParameterEditor}
import pl.touk.nussknacker.engine.api.parameter.ParameterName

case class SingleComponentConfig(
    params: Option[Map[ParameterName, ParameterConfig]],
    icon: Option[String],
    docsUrl: Option[String],
    componentGroup: Option[ComponentGroupName],
    // TODO We allow to define this id in the configuration as a work around for the problem
    //      that the components are duplicated across processing types - see notice in DesignerWideComponentId
    //      It should be probable called designerWideComponentId but we don't want to change it
    //      to not break the compatibility
    componentId: Option[DesignerWideComponentId],
    disabled: Boolean = false
)

object SingleComponentConfig {

  val zero: SingleComponentConfig = SingleComponentConfig(None, None, None, None, None)

  implicit val semigroup: Semigroup[SingleComponentConfig] = {
    implicit def takeLeftOptionSemi[T]: Semigroup[Option[T]] = Semigroup.instance[Option[T]] {
      case (None, None)    => None
      case (None, Some(x)) => Some(x)
      case (Some(x), _)    => Some(x)
    }

    implicit def takeLeftMapSemi[K, V]: Semigroup[Map[K, V]] = Semigroup.instance[Map[K, V]] { (x, y) =>
      val keys = x.keys ++ y.keys
      keys.map { k =>
        k -> (x.get(k) |+| y.get(k)).get
      }.toMap
    }

    implicit def naturalOptionMapSemi[K, V]: Semigroup[Option[Map[K, V]]] = Semigroup.instance[Option[Map[K, V]]] {
      case (None, None)       => None
      case (Some(x), None)    => Some(x)
      case (None, Some(x))    => Some(x)
      case (Some(x), Some(y)) => Some(x |+| y)
    }

    Semigroup.instance[SingleComponentConfig] { (x, y) =>
      SingleComponentConfig(
        x.params |+| y.params,
        x.icon |+| y.icon,
        x.docsUrl |+| y.docsUrl,
        x.componentGroup |+| y.componentGroup,
        x.componentId |+| y.componentId,
      )
    }
  }

}

case class ParameterConfig(
    defaultValue: Option[String],
    editor: Option[ParameterEditor],
    validators: Option[List[ParameterValidator]],
    label: Option[String],
    hintText: Option[String]
)

object ParameterConfig {
  val empty: ParameterConfig = ParameterConfig(None, None, None, None, None)
}

@JsonCodec case class ScenarioPropertyConfig(
    defaultValue: Option[String],
    editor: Option[SimpleParameterEditor],
    validators: Option[List[ParameterValidator]],
    label: Option[String],
    hintText: Option[String]
)

object ScenarioPropertyConfig {
  val empty: ScenarioPropertyConfig = ScenarioPropertyConfig(None, None, None, None, None)

  implicit val semigroup: Semigroup[ScenarioPropertyConfig] = {
    implicit def takeLeftOptionSemi[T]: Semigroup[Option[T]] = Semigroup.instance[Option[T]] {
      case (None, None)    => None
      case (None, Some(x)) => Some(x)
      case (Some(x), _)    => Some(x)
    }

    Semigroup.instance[ScenarioPropertyConfig] { (x, y) =>
      ScenarioPropertyConfig(
        x.defaultValue |+| y.defaultValue,
        x.editor |+| y.editor,
        x.validators |+| y.validators,
        x.label |+| y.label,
        x.hintText |+| y.hintText
      )
    }
  }

}
