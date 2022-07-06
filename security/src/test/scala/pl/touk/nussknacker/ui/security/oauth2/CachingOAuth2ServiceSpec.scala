package pl.touk.nussknacker.ui.security.oauth2

import com.github.benmanes.caffeine.cache.Ticker
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FunSpec, Matchers}
import pdi.jwt.{JwtAlgorithm, JwtCirce, JwtClaim}

import java.time.{Clock, Instant}
import scala.concurrent.Future
import scala.concurrent.duration.Deadline

class CachingOAuth2ServiceSpec extends FunSpec with ScalaFutures with Matchers with WithJwtOauth2Service {

  final override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  implicit val clock: Clock = Clock.systemUTC()

  private var currentTime = Deadline.now

  private var prev = Deadline.now


  private val ticker = new Ticker {
    override def read(): Long = {
      if (currentTime != prev) {
        println(currentTime)
        prev = currentTime
      }
      currentTime.time.toNanos
    }
  }

  var checkRecordings = Map[String, Int]()

  val recordingJwtOauth2Service = new OAuth2Service[OpenIdConnectUserInfo, DefaultOidcAuthorizationData] {
    override def obtainAuthorizationAndUserInfo(authorizationCode: String, redirectUri: String): Future[(DefaultOidcAuthorizationData, OpenIdConnectUserInfo)] =
      jwtOAuth2Service.obtainAuthorizationAndUserInfo(authorizationCode, redirectUri)

    override def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(OpenIdConnectUserInfo, Option[Instant])] = {
      checkRecordings = checkRecordings + (accessToken -> (checkRecordings.getOrElse(accessToken, 0) + 1))
      jwtOAuth2Service.checkAuthorizationAndObtainUserinfo(accessToken)
    }
  }


  val cachingOAuth2Service = new CachingOAuth2Service(recordingJwtOauth2Service, config, ticker)

  import scala.concurrent.duration._


  it("should cache token only for period when token is valid") {
    val token = JwtCirce.encode(JwtClaim().about("admin").to(audience).expiresIn(180), keyPair.getPrivate, JwtAlgorithm.RS256)
    checkRecordings.get(token) shouldBe None

    cachingOAuth2Service.checkAuthorizationAndObtainUserinfo(token).futureValue
    checkRecordings(token) shouldBe 1

    currentTime += 179.seconds
    cachingOAuth2Service.checkAuthorizationAndObtainUserinfo(token).futureValue
    checkRecordings(token) shouldBe 1

    currentTime += 1.seconds
    cachingOAuth2Service.checkAuthorizationAndObtainUserinfo(token).futureValue
    checkRecordings(token) shouldBe 2
  }
}
