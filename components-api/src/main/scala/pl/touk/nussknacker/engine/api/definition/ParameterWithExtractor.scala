package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.{LazyParameter, Params}
import pl.touk.nussknacker.engine.api.util.NotNothing

import scala.reflect.runtime.universe._

/**
 * It is helper class that holds runtime value type next to definition of parameter.
 * It reduce boilerplate defining `DynamicComponent` and reduce risk that definition of parameter
 * will desynchronize with implementation code using values
 */
//case class ParameterWithExtractor[V](parameter: Parameter) {
//
//  // todo: remove
//  def extractValue(params: Params): V = ???
//  //params.extractPresentValueUnsafe[V](parameter.name)
//
//}

// todo: remove

sealed trait ParameterWithExtractor[VALUE] {
  def parameter: Parameter
  def extractValue(params: Params): VALUE
}

final class MandatoryParameterWithExtractor[T](override val parameter: Parameter) extends ParameterWithExtractor[T] {

  override def extractValue(params: Params): T = params.extractUnsafe[T](parameter.name)
}

final class MandatoryLazyParameterWithExtractor[T <: AnyRef](override val parameter: Parameter)
    extends ParameterWithExtractor[LazyParameter[T]] {

  override def extractValue(params: Params): LazyParameter[T] =
    params.extractUnsafe[LazyParameter[T]](parameter.name)
}

final class OptionalParameterWithExtractor[T](override val parameter: Parameter)
    extends ParameterWithExtractor[Option[T]] {

  override def extractValue(params: Params): Option[T] = params.extract[T](parameter.name)
}

final class OptionalLazyParameterWithExtractor[T <: AnyRef](override val parameter: Parameter)
    extends ParameterWithExtractor[Option[LazyParameter[T]]] {

  override def extractValue(params: Params): Option[LazyParameter[T]] =
    params.extract[LazyParameter[T]](parameter.name)
}

object ParameterWithExtractor {

  def mandatory[T: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[T] = {
    val param = modify(Parameter[T](name))
    new MandatoryParameterWithExtractor[T](param)
  }

  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[LazyParameter[T]] = {
    val param = modify(Parameter[T](name).copy(isLazyParameter = true))
    new MandatoryLazyParameterWithExtractor[T](param)
  }

  def branchMandatory[T: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Map[String, T]] = {
    val param = modify(Parameter[T](name).copy(branchParam = true))
//    new ParameterWithExtractor[Map[String, T]](param)
    ???
  } // todo:

  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Map[String, LazyParameter[T]]] = {
    val param = modify(Parameter[T](name).copy(branchParam = true, isLazyParameter = true))
//    new ParameterWithExtractor[Map[String, LazyParameter[T]]](param)
    ???
  } // todo:

  def optional[T: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Option[T]] = {
    val param = modify(Parameter.optional[T](name))
    new OptionalParameterWithExtractor[T](param)
  }

  def lazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Option[LazyParameter[T]]] = {
    val param = modify(Parameter.optional[T](name).copy(isLazyParameter = true))
    new OptionalLazyParameterWithExtractor[T](param)
  }

  def branchOptional[T: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[T] = {
    val param = modify(Parameter.optional[T](name).copy(branchParam = true))
//    new ParameterWithExtractor[T](param)
    ???
  } // todo:

  def branchLazyOptional[T <: AnyRef: TypeTag: NotNothing](
      name: String,
      modify: Parameter => Parameter = identity
  ): ParameterWithExtractor[Map[String, LazyParameter[T]]] = {
    val param = modify(Parameter.optional[T](name).copy(branchParam = true, isLazyParameter = true))
//    new ParameterWithExtractor[Map[String, LazyParameter[T]]](param)
    ???
  } // todo:

}
