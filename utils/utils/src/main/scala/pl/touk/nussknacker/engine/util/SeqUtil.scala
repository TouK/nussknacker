package pl.touk.nussknacker.engine.util

object SeqUtil {

  def maxByOption[A, B: Ordering](seq: Seq[A])(f: A => B): Option[A] = {
    if (seq.isEmpty) None
    else Some(seq.maxBy(f))
  }

}
