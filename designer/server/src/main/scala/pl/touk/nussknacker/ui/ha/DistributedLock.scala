package pl.touk.nussknacker.ui.ha

import pl.touk.nussknacker.engine.api.db.{DbRef, NuPostgresProfile}
import slick.jdbc.JdbcBackend

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

trait DistributedLock {

  /** Returns the lock_until timestamp if the lock was acquired or renewed, None otherwise. */
  def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]]

  /** Returns true if the lock was held by this instance and successfully released. */
  def release(name: String): Future[Boolean]

}

object NoOpDistributedLock extends DistributedLock {
  override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] =
    Future.successful(Some(Instant.MAX))
  override def release(name: String): Future[Boolean] = Future.successful(true)
}

object DistributedLock {

  def apply(haMode: HaMode, dbRef: DbRef)(implicit ec: ExecutionContext): DistributedLock =
    haMode match {
      case HaMode.Disabled(_) => NoOpDistributedLock
      case HaMode.Enabled(instanceId, _, _, lockQueryTimeout) =>
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

  override def acquireOrRenew(name: String, duration: FiniteDuration): Future[Option[Instant]] =
    run(acquireOrRenewLock(name, duration.toMillis))

  override def release(name: String): Future[Boolean] =
    run(releaseLock(name))

  // toSeconds truncates; JDBC setQueryTimeout(0) means "no timeout", so round up to at least 1 s
  private val lockQueryTimeoutSeconds = Math.max(1, lockQueryTimeout.toSeconds.toInt)

  private def acquireOrRenewLock(name: String, durationMillis: Long): DBIO[Option[Instant]] = {
    sql"""INSERT INTO #${profile.schemaName}.distributed_locks AS dl (name, lock_until, locked_at, locked_by)
           VALUES (
             $name,
             CURRENT_TIMESTAMP + INTERVAL '#${durationMillis} milliseconds',
             CURRENT_TIMESTAMP,
             $instanceId
           )
           ON CONFLICT (name) DO UPDATE SET
             lock_until = EXCLUDED.lock_until,
             locked_at  = CURRENT_TIMESTAMP,
             locked_by  = EXCLUDED.locked_by
           WHERE dl.lock_until <= CURRENT_TIMESTAMP
              OR dl.locked_by = $instanceId
           RETURNING lock_until"""
      .as[java.sql.Timestamp]
      .withStatementParameters(statementInit = _.setQueryTimeout(lockQueryTimeoutSeconds))
  }.map(_.headOption.map(_.toInstant))

  private def releaseLock(name: String): DBIO[Boolean] = {
    // lock_until > CURRENT_TIMESTAMP ensures idempotency: a second release call finds lock_until already in the past → returns false
    sqlu"""UPDATE #${profile.schemaName}.distributed_locks
             SET lock_until = CURRENT_TIMESTAMP
             WHERE name = $name AND locked_by = $instanceId AND lock_until > CURRENT_TIMESTAMP"""
      .withStatementParameters(statementInit = _.setQueryTimeout(lockQueryTimeoutSeconds))
      .map(_ > 0)
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
