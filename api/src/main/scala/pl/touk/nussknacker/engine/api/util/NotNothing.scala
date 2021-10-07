package pl.touk.nussknacker.engine.api.util

import scala.annotation.implicitNotFound

/**
 * Copied from kafka.utils.NotNothing
 * This is a trick to prevent the compiler from inferring the Nothing type in cases where it would be a bug to do
 * so. An example is the following method:
 *
 * ```
 * def body[T <: AbstractRequest](implicit classTag: ClassTag[T], nn: NotNothing[T]): T
 * ```
 *
 * If we remove the `nn` parameter and we invoke it without any type parameters (e.g. `request.body`), `Nothing` would
 * be inferred, which is not desirable. As defined above, we get a helpful compiler error asking the user to provide
 * the type parameter explicitly.
 */
@implicitNotFound("Unable to infer type parameter, please provide it explicitly.")
trait NotNothing[T]

object NotNothing {
  private val evidence: NotNothing[Any] = new Object with NotNothing[Any]

  implicit def notNothingEvidence[T](implicit n: T =:= T): NotNothing[T] = evidence.asInstanceOf[NotNothing[T]]
}

