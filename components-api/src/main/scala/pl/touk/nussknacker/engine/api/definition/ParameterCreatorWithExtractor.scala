package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.{LazyParameter, Params}
import pl.touk.nussknacker.engine.api.util.NotNothing

import scala.reflect.runtime.universe._

/**
 * It is helper class that holds runtime value type next to definition of parameter.
 * It reduce boilerplate defining `DynamicComponent` and reduce risk that definition of parameter
 * will desynchronize with implementation code using values
 */
sealed trait ParameterCreatorWithExtractor[VALUE, S] extends Serializable {
  def parameterName: ParameterName
  def createParameter: S => Parameter
  def extractValue(params: Params): VALUE
}

object ParameterCreatorWithExtractor {

  def mandatory[T: TypeTag: NotNothing, S](
      name: ParameterName,
      create: (S, Parameter) => Parameter
  ): ParameterCreatorWithExtractor[T, S] = {
    new MandatoryParameterCreatorWithExtractor(
      name,
      s => create(s, Parameter[T](name))
    )
//    MandatoryParameterCreatorWithExtractor.create(modify(Parameter[T](name)))
  }

  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing, S](
      name: ParameterName,
      create: (S, Parameter) => Parameter
  ): ParameterCreatorWithExtractor[LazyParameter[T], S] = {
    new MandatoryLazyParameterCreatorWithExtractor(
      name,
      s => create(s, Parameter[T](name))
    )
//    MandatoryLazyParameterCreatorWithExtractor.create[T](modify(Parameter[T](name).copy(isLazyParameter = true)))
  }

  def branchMandatory[T: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterCreatorWithExtractor[Map[String, T]] = {
    MandatoryBranchParameterCreatorWithExtractor.create(modify(Parameter[T](name)))
  }

  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterCreatorWithExtractor[Map[String, LazyParameter[T]]] = {
    MandatoryBranchLazyParameterCreatorWithExtractor.create(modify(Parameter[T](name).copy(isLazyParameter = true)))
  }

  def optional[T: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterCreatorWithExtractor[Option[T]] = {
    // todo: optional should be moved to OptionalParameterWithExtractor
    OptionalParameterCreatorWithExtractor.create[T](modify(Parameter.optional[T](name)))
  }

  def lazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterCreatorWithExtractor[Option[LazyParameter[T]]] = {
    OptionalLazyParameterCreatorWithExtractor.create[T](
      modify(Parameter.optional[T](name).copy(isLazyParameter = true))
    )
  }

  def branchOptional[T: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterCreatorWithExtractor[Option[Map[String, T]]] = {
    // todo: optional should be moved to OptionalParameterWithExtractor
    OptionalBranchParameterCreatorWithExtractor.create[T](modify(Parameter.optional[T](name)))
  }

  def branchLazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterCreatorWithExtractor[Option[Map[String, LazyParameter[T]]]] = {
    // todo: optional should be moved to OptionalParameterWithExtractor
    OptionalBranchLazyParameterCreatorWithExtractor.create[T](
      modify(Parameter.optional[T](name).copy(isLazyParameter = true))
    )
  }

}

private final class MandatoryParameterCreatorWithExtractor[T, S](
    override val parameterName: ParameterName,
    override val createParameter: S => Parameter
) extends ParameterCreatorWithExtractor[T, S] {

  override def extractValue(params: Params): T = params.extractMandatory[T](parameterName)

}

private final class MandatoryLazyParameterCreatorWithExtractor[T <: AnyRef, S](
    override val parameterName: ParameterName,
    override val createParameter: S => Parameter
) extends ParameterCreatorWithExtractor[LazyParameter[T], S] {

  override def extractValue(params: Params): LazyParameter[T] =
    params.extractMandatory[LazyParameter[T]](parameterName)
}

private final class MandatoryBranchParameterCreatorWithExtractor[T] private (override val createParameter: Parameter)
    extends ParameterCreatorWithExtractor[Map[String, T]] {

  override def extractValue(params: Params): Map[String, T] =
    params.extractMandatory[Map[String, T]](createParameter.name)
}

private object MandatoryBranchParameterCreatorWithExtractor {

  def create[T](parameter: Parameter): MandatoryBranchParameterCreatorWithExtractor[T] = {
    new MandatoryBranchParameterCreatorWithExtractor[T](parameter.copy(branchParam = true))
  }

}

private final class MandatoryBranchLazyParameterCreatorWithExtractor[T <: AnyRef] private (
    override val createParameter: Parameter
) extends ParameterCreatorWithExtractor[Map[String, LazyParameter[T]]] {

  override def extractValue(params: Params): Map[String, LazyParameter[T]] =
    params.extractMandatory[Map[String, LazyParameter[T]]](createParameter.name)
}

private object MandatoryBranchLazyParameterCreatorWithExtractor {

  def create[T <: AnyRef](parameter: Parameter): MandatoryBranchLazyParameterCreatorWithExtractor[T] = {
    new MandatoryBranchLazyParameterCreatorWithExtractor[T](parameter.copy(branchParam = true, isLazyParameter = true))
  }

}

private final class OptionalParameterCreatorWithExtractor[T] private (override val createParameter: Parameter)
    extends ParameterCreatorWithExtractor[Option[T]] {

  override def extractValue(params: Params): Option[T] = params.extract[T](createParameter.name)
}

private object OptionalParameterCreatorWithExtractor {

  def create[T](parameter: Parameter): OptionalParameterCreatorWithExtractor[T] = {
    new OptionalParameterCreatorWithExtractor[T](parameter)
  }

}

private final class OptionalLazyParameterCreatorWithExtractor[T <: AnyRef](override val createParameter: Parameter)
    extends ParameterCreatorWithExtractor[Option[LazyParameter[T]]] {

  override def extractValue(params: Params): Option[LazyParameter[T]] =
    params.extract[LazyParameter[T]](createParameter.name)
}

private object OptionalLazyParameterCreatorWithExtractor {

  def create[T <: AnyRef](parameter: Parameter): OptionalLazyParameterCreatorWithExtractor[T] = {
    new OptionalLazyParameterCreatorWithExtractor[T](parameter.copy(isLazyParameter = true))
  }

}

private final class OptionalBranchParameterCreatorWithExtractor[T] private (override val createParameter: Parameter)
    extends ParameterCreatorWithExtractor[Option[Map[String, T]]] {

  override def extractValue(params: Params): Option[Map[String, T]] =
    params.extract[Map[String, T]](createParameter.name)
}

private object OptionalBranchParameterCreatorWithExtractor {

  def create[T](parameter: Parameter): OptionalBranchParameterCreatorWithExtractor[T] = {
    new OptionalBranchParameterCreatorWithExtractor[T](parameter.copy(branchParam = true))
  }

}

private final class OptionalBranchLazyParameterCreatorWithExtractor[T <: AnyRef] private (
    override val createParameter: Parameter
) extends ParameterCreatorWithExtractor[Option[Map[String, LazyParameter[T]]]] {

  override def extractValue(params: Params): Option[Map[String, LazyParameter[T]]] =
    params.extract[Map[String, LazyParameter[T]]](createParameter.name)
}

private object OptionalBranchLazyParameterCreatorWithExtractor {

  def create[T <: AnyRef](parameter: Parameter): OptionalBranchLazyParameterCreatorWithExtractor[T] = {
    new OptionalBranchLazyParameterCreatorWithExtractor[T](parameter.copy(branchParam = true, isLazyParameter = true))
  }

}
