package pl.touk.esp.engine.api.lazyy

import pl.touk.esp.engine.api.lazyy.LazyContext._

trait LazyContext {

  def evaluatedValues: Map[Key, Any]

  def apply[T](serviceId: String, params: Params): T =
    getOrElse(serviceId, params, throw new RuntimeException(s"Value for service: $serviceId is not evaluated yet"))

  def getOrElse[T](serviceId: String, params: Params, default: => T) =
    get(serviceId, params).getOrElse(default)

  def get[T](serviceId: String, params: Params): Option[T] =
    evaluatedValues.get((serviceId, params)).map(_.asInstanceOf[T])

  def withEvaluatedValue(serviceId: String, params: Params, value: Any): LazyContext =
    withEvaluatedValues(Map((serviceId, params) -> value))

  def withEvaluatedValues(otherEvaluatedValues: Map[Key, Any]): LazyContext

}

object LazyContext {

  type Key = (String, Params) // (serviceId, params)
  type Params = Map[String, Any]


}