package pl.touk.nussknacker.engine.util

object multiplicity {

  sealed trait Multiplicity[A]

  case class Empty[T]() extends Multiplicity[T]

  case class One[T](value: T) extends Multiplicity[T]

  case class Many[T](many: List[T]) extends Multiplicity[T]

  object Multiplicity {
    def apply[T](args: List[T]): Multiplicity[T] = args match {
      case Nil => Empty[T]()
      case one :: Nil => One(one)
      case many => Many(many)
    }
  }

}
