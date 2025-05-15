package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.admin.Admin
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify}
import org.scalatest.Inside.inside
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.util.{Failure, Success, Try}

class LazyKafkaAdminClientTest extends AnyFreeSpec with Matchers {

  private val kafkaConfig = KafkaConfig(
    kafkaProperties = Some(Map("bootstrap.servers" -> "host1:9092,host2:9092")),
    kafkaEspProperties = None,
  )

  "For single Kafka config" - {

    "should create admin client only once" in {
      var createClientInvokedTimes = 0
      def createClient = {
        createClientInvokedTimes += 1
        mock[Admin]
      }
      val lazyClient = new LazyKafkaAdminClient(new LazyKafkaAdminClientCache, kafkaConfig, createClient)

      val returnedClient1 = lazyClient.getOrCreate
      val returnedClient2 = lazyClient.getOrCreate
      val returnedClient3 = lazyClient.getOrCreate

      returnedClient1 should (be theSameInstanceAs returnedClient2 and be theSameInstanceAs returnedClient3)
      createClientInvokedTimes shouldBe 1
    }

    "should create admin client again after previous failure" in {
      var createClientInvokedTimes = 0
      def createClient = {
        createClientInvokedTimes += 1
        if (createClientInvokedTimes == 1) {
          throw new RuntimeException("Could not connect to Kafka broker")
        } else {
          mock[Admin]
        }
      }
      val lazyClient = new LazyKafkaAdminClient(new LazyKafkaAdminClientCache, kafkaConfig, createClient)

      val returnedClient1 = Try(lazyClient.getOrCreate)
      val returnedClient2 = Try(lazyClient.getOrCreate)
      val returnedClient3 = Try(lazyClient.getOrCreate)

      inside(returnedClient1) { case Failure(exception) =>
        exception.getMessage shouldBe "Could not connect to Kafka broker"
      }
      inside(returnedClient2, returnedClient3) { case (Success(client2), Success(client3)) =>
        client2 should be theSameInstanceAs client3
      }
      createClientInvokedTimes shouldBe 2
    }

    "should close admin client" in {
      val client       = mock[Admin]
      def createClient = client
      val lazyClient   = new LazyKafkaAdminClient(new LazyKafkaAdminClientCache, kafkaConfig, createClient)

      lazyClient.getOrCreate
      lazyClient.close()

      verify(client).close(any[java.time.Duration]())
    }

    "should not try to close never used admin client" in {
      var createClientInvokedTimes = 0
      val client                   = mock[Admin]
      def createClient = {
        createClientInvokedTimes += 1
        client
      }
      val lazyClient = new LazyKafkaAdminClient(new LazyKafkaAdminClientCache, kafkaConfig, createClient)

      lazyClient.close()

      verify(client, never()).close(any[java.time.Duration]())
      createClientInvokedTimes shouldBe 0
    }
  }

}
