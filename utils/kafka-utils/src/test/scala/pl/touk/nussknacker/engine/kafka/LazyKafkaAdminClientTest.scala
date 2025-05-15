package pl.touk.nussknacker.engine.kafka

import org.apache.kafka.clients.admin.Admin
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.Inside.inside
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.util.{Failure, Success, Try}

class LazyKafkaAdminClientTest extends AnyFreeSpec with Matchers {

  "For single Kafka config" - {

    val kafkaConfig = KafkaConfig(
      kafkaProperties = Some(Map("bootstrap.servers" -> "host1:9092,host2:9092")),
      kafkaEspProperties = None,
    )

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

    "should close admin client once" in {
      val client     = mock[Admin]
      val lazyClient = new LazyKafkaAdminClient(new LazyKafkaAdminClientCache, kafkaConfig, client)

      lazyClient.getOrCreate
      lazyClient.close()
      lazyClient.close()
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

    "should not try to close again client closed with failure" in {
      val client = mock[Admin]
      when(client.close(any[java.time.Duration])).thenThrow(new RuntimeException("Could not close client"))
      val lazyClient = new LazyKafkaAdminClient(new LazyKafkaAdminClientCache, kafkaConfig, client)

      lazyClient.getOrCreate
      assertThrows[RuntimeException] { lazyClient.close() }
      lazyClient.close()
      lazyClient.close()

      verify(client).close(any[java.time.Duration]())
    }
  }

  "For multiple kafka configs" - {

    val kafkaConfig1 = KafkaConfig(
      kafkaProperties = Some(Map("bootstrap.servers" -> "host1:9092,host2:9092")),
      kafkaEspProperties = None,
    )
    val kafkaConfig2 = KafkaConfig(
      kafkaProperties = Some(Map("bootstrap.servers" -> "host3:9092,host4:9092")),
      kafkaEspProperties = None,
    )

    "should create admin client only once for each config" in {
      val cache       = new LazyKafkaAdminClientCache
      val lazyClient1 = new LazyKafkaAdminClient(cache, kafkaConfig1, mock[Admin])
      val lazyClient2 = new LazyKafkaAdminClient(cache, kafkaConfig2, mock[Admin])

      val returnedClient1ForConfig1 = lazyClient1.getOrCreate
      val returnedClient2ForConfig1 = lazyClient1.getOrCreate
      val returnedClient1ForConfig2 = lazyClient2.getOrCreate
      val returnedClient2ForConfig2 = lazyClient2.getOrCreate

      returnedClient1ForConfig1 shouldBe theSameInstanceAs(returnedClient2ForConfig1)
      returnedClient1ForConfig2 shouldBe theSameInstanceAs(returnedClient2ForConfig2)
      returnedClient1ForConfig1 should not be theSameInstanceAs(returnedClient1ForConfig2)
    }

    "should close admin client separately for each config" in {
      val client1     = mock[Admin]
      val client2     = mock[Admin]
      val cache       = new LazyKafkaAdminClientCache
      val lazyClient1 = new LazyKafkaAdminClient(cache, kafkaConfig1, client1)
      val lazyClient2 = new LazyKafkaAdminClient(cache, kafkaConfig2, client2)

      lazyClient1.getOrCreate
      lazyClient2.getOrCreate
      lazyClient1.close()
      lazyClient2.close()

      verify(client1).close(any[java.time.Duration]())
      verify(client2).close(any[java.time.Duration]())
    }
  }

}
