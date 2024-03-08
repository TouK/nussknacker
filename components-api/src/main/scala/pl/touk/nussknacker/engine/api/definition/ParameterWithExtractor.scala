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
sealed trait ParameterWithExtractor[VALUE] extends Serializable {
  def parameter: Parameter
  def extractValue(params: Params): VALUE
}

object ParameterWithExtractor {

  def mandatory[T: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[T] = {
    MandatoryParameterWithExtractor.create(modify(Parameter[T](name)))
  }

  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[LazyParameter[T]] = {
    MandatoryLazyParameterWithExtractor.create[T](modify(Parameter[T](name).copy(isLazyParameter = true)))
  }

  def branchMandatory[T: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Map[String, T]] = {
    MandatoryBranchParameterWithExtractor.create(modify(Parameter[T](name)))
  }

  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Map[String, LazyParameter[T]]] = {
    MandatoryBranchLazyParameterWithExtractor.create(modify(Parameter[T](name).copy(isLazyParameter = true)))
  }

  def optional[T: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Option[T]] = {
    // todo: optional should be moved to OptionalParameterWithExtractor
    OptionalParameterWithExtractor.create[T](modify(Parameter.optional[T](name)))
  }

  def lazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Option[LazyParameter[T]]] = {
    OptionalLazyParameterWithExtractor.create[T](modify(Parameter.optional[T](name).copy(isLazyParameter = true)))
  }

  def branchOptional[T: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Option[Map[String, T]]] = {
    // todo: optional should be moved to OptionalParameterWithExtractor
    OptionalBranchParameterWithExtractor.create[T](modify(Parameter.optional[T](name)))
  }

  def branchLazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: ParameterName,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Option[Map[String, LazyParameter[T]]]] = {
    // todo: optional should be moved to OptionalParameterWithExtractor
    OptionalBranchLazyParameterWithExtractor.create[T](modify(Parameter.optional[T](name).copy(isLazyParameter = true)))
  }

}

private final class MandatoryParameterWithExtractor[T] private (override val parameter: Parameter)
    extends ParameterWithExtractor[T] {

  override def extractValue(params: Params): T = params.extractMandatory[T](parameter.name)
}

private object MandatoryParameterWithExtractor {
  def create[T](parameter: Parameter): MandatoryParameterWithExtractor[T] =
    new MandatoryParameterWithExtractor[T](parameter)
}

private final class MandatoryLazyParameterWithExtractor[T <: AnyRef] private (override val parameter: Parameter)
    extends ParameterWithExtractor[LazyParameter[T]] {

  override def extractValue(params: Params): LazyParameter[T] =
    params.extractMandatory[LazyParameter[T]](parameter.name)
}

private object MandatoryLazyParameterWithExtractor {
  def create[T <: AnyRef](parameter: Parameter): MandatoryLazyParameterWithExtractor[T] =
    new MandatoryLazyParameterWithExtractor[T](parameter.copy(isLazyParameter = true))
}

private final class MandatoryBranchParameterWithExtractor[T] private (override val parameter: Parameter)
    extends ParameterWithExtractor[Map[String, T]] {

  override def extractValue(params: Params): Map[String, T] =
    params.extractMandatory[Map[String, T]](parameter.name)
}

private object MandatoryBranchParameterWithExtractor {

  def create[T](parameter: Parameter): MandatoryBranchParameterWithExtractor[T] = {
    new MandatoryBranchParameterWithExtractor[T](parameter.copy(branchParam = true))
  }

}

private final class MandatoryBranchLazyParameterWithExtractor[T <: AnyRef] private (override val parameter: Parameter)
    extends ParameterWithExtractor[Map[String, LazyParameter[T]]] {

  override def extractValue(params: Params): Map[String, LazyParameter[T]] =
    params.extractMandatory[Map[String, LazyParameter[T]]](parameter.name)
}

private object MandatoryBranchLazyParameterWithExtractor {

  def create[T <: AnyRef](parameter: Parameter): MandatoryBranchLazyParameterWithExtractor[T] = {
    new MandatoryBranchLazyParameterWithExtractor[T](parameter.copy(branchParam = true, isLazyParameter = true))
  }

}

private final class OptionalParameterWithExtractor[T] private (override val parameter: Parameter)
    extends ParameterWithExtractor[Option[T]] {

  override def extractValue(params: Params): Option[T] = params.extract[T](parameter.name)
}

private object OptionalParameterWithExtractor {

  def create[T](parameter: Parameter): OptionalParameterWithExtractor[T] = {
    new OptionalParameterWithExtractor[T](parameter)
  }

}

private final class OptionalLazyParameterWithExtractor[T <: AnyRef](override val parameter: Parameter)
    extends ParameterWithExtractor[Option[LazyParameter[T]]] {

  override def extractValue(params: Params): Option[LazyParameter[T]] =
    params.extract[LazyParameter[T]](parameter.name)
}

private object OptionalLazyParameterWithExtractor {

  def create[T <: AnyRef](parameter: Parameter): OptionalLazyParameterWithExtractor[T] = {
    new OptionalLazyParameterWithExtractor[T](parameter.copy(isLazyParameter = true))
  }

}

private final class OptionalBranchParameterWithExtractor[T] private (override val parameter: Parameter)
    extends ParameterWithExtractor[Option[Map[String, T]]] {

  override def extractValue(params: Params): Option[Map[String, T]] =
    params.extract[Map[String, T]](parameter.name)
}

private object OptionalBranchParameterWithExtractor {

  def create[T](parameter: Parameter): OptionalBranchParameterWithExtractor[T] = {
    new OptionalBranchParameterWithExtractor[T](parameter.copy(branchParam = true))
  }

}

private final class OptionalBranchLazyParameterWithExtractor[T <: AnyRef] private (override val parameter: Parameter)
    extends ParameterWithExtractor[Option[Map[String, LazyParameter[T]]]] {

  override def extractValue(params: Params): Option[Map[String, LazyParameter[T]]] =
    params.extract[Map[String, LazyParameter[T]]](parameter.name)
}

private object OptionalBranchLazyParameterWithExtractor {

  def create[T <: AnyRef](parameter: Parameter): OptionalBranchLazyParameterWithExtractor[T] = {
    new OptionalBranchLazyParameterWithExtractor[T](parameter.copy(branchParam = true, isLazyParameter = true))
  }

}
