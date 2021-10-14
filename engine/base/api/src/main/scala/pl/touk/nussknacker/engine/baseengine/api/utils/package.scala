package pl.touk.nussknacker.engine.baseengine.api

import cats.data.Writer
import pl.touk.nussknacker.engine.baseengine.api.BaseScenarioEngineTypes.ResultType

package object utils {

  //is there simpler way of doing this?
  implicit class GenericListResultTypeOps[T](self: ResultType[T]) {
    def add(other: ResultType[T]): ResultType[T] = self.bimap(_ ++ other.run._1, _ ++ other.run._2)
  }

  def sequence[T](list: List[ResultType[T]]): ResultType[T] = list.foldLeft(Writer.value(Nil): ResultType[T])(_.add(_))

}
