package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.api.util.NotNothing

import scala.reflect.runtime.universe._

/**
 * It is helper class that holds runtime value type next to definition of parameter.
 * It reduce boilerplate defining `GenericNodeTransformation` and reduce risk that definition of parameter
 * will desynchronize with implementation code using values
 */
case class ParameterWithExtractor[V](parameter: Parameter) {

  def extractValue(params: Map[String, Any]): V = params(parameter.name).asInstanceOf[V]

}

object ParameterWithExtractor {

  def mandatory[T: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[T] = {
    val param = modify(Parameter[T](name))
    new ParameterWithExtractor[T](param)
  }

  def lazyMandatory[T <: AnyRef: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[LazyParameter[T]] = {
    val param = modify(Parameter[T](name).copy(isLazyParameter = true))
    new ParameterWithExtractor[LazyParameter[T]](param)
  }

  def branchMandatory[T: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[Map[String, T]] = {
    val param = modify(Parameter[T](name).copy(branchParam = true))
    new ParameterWithExtractor[Map[String, T]](param)
  }

  def branchLazyMandatory[T <: AnyRef: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[Map[String, LazyParameter[T]]] = {
    val param = modify(Parameter[T](name).copy(branchParam = true, isLazyParameter = true))
    new ParameterWithExtractor[Map[String, LazyParameter[T]]](param)
  }

  def optional[T: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[T] = {
    val param = modify(Parameter.optional[T](name))
    new ParameterWithExtractor(param)
  }

  def lazyOptional[T <: AnyRef: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[LazyParameter[T]] = {
    val param = modify(Parameter.optional[T](name).copy(isLazyParameter = true))
    new ParameterWithExtractor[LazyParameter[T]](param)
  }

  def branchOptional[T: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[T] = {
    val param = modify(Parameter.optional[T](name).copy(branchParam = true))
    new ParameterWithExtractor[T](param)
  }

  def branchLazyOptional[T <: AnyRef: TypeTag: NotNothing](name: String, modify: Parameter => Parameter = identity): ParameterWithExtractor[Map[String, LazyParameter[T]]] = {
    val param = modify(Parameter.optional[T](name).copy(branchParam = true, isLazyParameter = true))
    new ParameterWithExtractor[Map[String, LazyParameter[T]]](param)
  }

}
