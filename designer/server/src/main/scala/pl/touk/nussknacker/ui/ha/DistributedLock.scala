package pl.touk.nussknacker.ui.ha

import pl.touk.nussknacker.engine.api.db.{DbRef, NuPostgresProfile}
import slick.jdbc.JdbcBackend

import java.time.{Clock, Instant}
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

  def apply(haMode: HaMode, dbRef: DbRef, clock: Clock)(implicit ec: ExecutionContext): DistributedLock =
    haMode match {
      case HaMode.Disabled(_)      => NoOpDistributedLock
      case enabled: HaMode.Enabled => SlickDistributedLock(dbRef, enabled.instanceId, enabled.lockQueryTimeout, clock)
    }

}

class SlickDistributedLock(
    db: JdbcBackend.Database,
    profile: NuPostgresProfile,
    instanceId: String,
    lockQueryTimeout: FiniteDuration,
    clock: Clock,
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
    // Leadership checks compare validUntil against the local clock, so we derive it from the local
    // clock rather than returning the DB-side lock_until (computed from the DB clock) — mixing clocks
    // could, under skew, let a node stay leader past the DB lock's real expiry. Capturing it just
    // before the INSERT (deferred to run time via flatMap) keeps validUntil no later than lock_until.
    DBIO.successful(()).flatMap { _ =>
      val validUntil = clock.instant().plusMillis(durationMillis)
      sqlu"""INSERT INTO "#${profile.schemaName}"."distributed_locks" AS dl (name, lock_until, locked_at, locked_by)
             VALUES (
               $name,
               CURRENT_TIMESTAMP + INTERVAL '#${durationMillis} milliseconds',
               CURRENT_TIMESTAMP,
               $instanceId
             )
             ON CONFLICT (name) DO UPDATE SET
               lock_until = EXCLUDED.lock_until,
               locked_at  = EXCLUDED.locked_at,
               locked_by  = EXCLUDED.locked_by
             WHERE dl.lock_until <= CURRENT_TIMESTAMP
                OR dl.locked_by = EXCLUDED.locked_by"""
        .withStatementParameters(statementInit = _.setQueryTimeout(lockQueryTimeoutSeconds))
        .map(rowsAffected => if (rowsAffected > 0) Some(validUntil) else None)
    }
  }

  private def releaseLock(name: String): DBIO[Boolean] = {
    // lock_until > CURRENT_TIMESTAMP ensures idempotency: a second release call finds lock_until already in the past → returns false
    sqlu"""UPDATE "#${profile.schemaName}"."distributed_locks"
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
      clock: Clock,
  )(implicit ec: ExecutionContext): DistributedLock =
    dbRef.profile match {
      case pg: NuPostgresProfile => new SlickDistributedLock(dbRef.db, pg, instanceId, lockQueryTimeout, clock)
      case other =>
        throw new IllegalStateException(
          s"HA mode requires PostgreSQL. Unsupported database profile: ${other.getClass.getSimpleName}."
        )
    }

}
