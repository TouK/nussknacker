package pl.touk.nussknacker.ui.util

/*
TODO: Consider alternative API:
  def liftVersion: CURRENT_VERSION => Option[NEXT_VERSION]
  def downgradeVersion: CURRENT_VERSION => Option[PREV_VERSION]
 */
trait ApiAdapter[D] {
  def liftVersion: D => D
  def downgradeVersion: D => D
}
