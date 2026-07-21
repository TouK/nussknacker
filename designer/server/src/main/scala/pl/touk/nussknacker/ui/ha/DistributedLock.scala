package pl.touk.nussknacker.ui.ha

import pl.touk.nussknacker.engine.api.db.{DbRef, NuPostgresProfile}
import slick.jdbc.JdbcBackend

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

trait DistributedLock {

  // Owner-bypass: succeeds if the lock is expired OR already held by this instance
  def acquireOrRenew(name: String, duration: FiniteDuration): Future[Boolean]

  def release(name: String): Future[Unit]

}

object NoOpDistributedLock extends DistributedLock {
  override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Boolean] = Future.successful(true)
  override def release(name: String): Future[Unit]                                     = Future.unit
}

object DistributedLock {

  def apply(haMode: HaMode, dbRef: DbRef)(implicit ec: ExecutionContext): DistributedLock =
    haMode match {
      case HaMode.Disabled => NoOpDistributedLock
      case HaMode.Enabled(instanceId, _, _, _, lockQueryTimeout) =>
        SlickDistributedLock(dbRef, instanceId, lockQueryTimeout)
    }

}

class SlickDistributedLock(
    db: JdbcBackend.Database,
    profile: NuPostgresProfile,
    instanceId: String,
    lockQueryTimeout: FiniteDuration,
)(implicit executionContext: ExecutionContext)
    extends DistributedLock {

  import profile.apiWithEnforcedSchema._

  override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Boolean] =
    run(acquireOrRenewLock(name, duration.toMillis))

  override def release(name: String): Future[Unit] =
    run(releaseLock(name))

  // toSeconds truncates; JDBC setQueryTimeout(0) means "no timeout", so round up to at least 1 s
  private val lockQueryTimeoutSeconds = Math.max(1, lockQueryTimeout.toSeconds.toInt)

  private def acquireOrRenewLock(name: String, durationMillis: Long): DBIO[Boolean] = {
    sqlu"""INSERT INTO "#${profile.schemaName}"."distributed_locks" (name, lock_until, locked_at, locked_by)
             VALUES (
               $name,
               CURRENT_TIMESTAMP + CAST($durationMillis || ' milliseconds' AS INTERVAL),
               CURRENT_TIMESTAMP,
               $instanceId
             )
             ON CONFLICT (name) DO UPDATE SET
               lock_until = EXCLUDED.lock_until,
               locked_at  = CURRENT_TIMESTAMP,
               locked_by  = EXCLUDED.locked_by
             WHERE "distributed_locks"."lock_until" < CURRENT_TIMESTAMP
                OR "distributed_locks"."locked_by" = $instanceId"""
      .withStatementParameters(statementInit = _.setQueryTimeout(lockQueryTimeoutSeconds))
  }.map(_ > 0)

  private def releaseLock(name: String): DBIO[Unit] = {
    sqlu"""UPDATE "#${profile.schemaName}"."distributed_locks"
             SET lock_until = CURRENT_TIMESTAMP
             WHERE name = $name AND locked_by = $instanceId"""
      .withStatementParameters(statementInit = _.setQueryTimeout(lockQueryTimeoutSeconds))
      .map(_ => ())
  }

  private def run[T](action: DBIO[T]): Future[T] = db.run(action)

}

object SlickDistributedLock {

  def apply(
      dbRef: DbRef,
      instanceId: String,
      lockQueryTimeout: FiniteDuration,
  )(implicit ec: ExecutionContext): DistributedLock =
    dbRef.profile match {
      case pg: NuPostgresProfile => new SlickDistributedLock(dbRef.db, pg, instanceId, lockQueryTimeout)
      case other =>
        throw new IllegalStateException(
          s"HA mode requires PostgreSQL. Unsupported database profile: ${other.getClass.getSimpleName}."
        )
    }

}
