package pl.touk.nussknacker.engine.util

object multiplicity {

  sealed trait Multiplicity[A] {
    def orElse(alternative: Multiplicity[A]): Multiplicity[A] = this
  }

  case class Empty[T]() extends Multiplicity[T] {
    override def orElse(alternative: Multiplicity[T]): Multiplicity[T] = alternative
  }

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
