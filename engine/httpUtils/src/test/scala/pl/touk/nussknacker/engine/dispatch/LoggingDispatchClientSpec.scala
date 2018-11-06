package pl.touk.nussknacker.engine.dispatch

import org.asynchttpclient.{AsyncHttpClient, DefaultAsyncHttpClient}
import dispatch.Http
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.dispatch.LoggingDispatchClientSpec.Mocks


class LoggingDispatchClientSpec extends FlatSpec with Matchers {
  it should "close inner client" in {
    var isClosed = false
    val client = Mocks.asyncHandler {
      isClosed = true
    }
    LoggingDispatchClient(this.getClass.getSimpleName, client, "", None).shutdown()
    isClosed shouldBe true
  }
}

object LoggingDispatchClientSpec {

  object Mocks {
    def asyncHandler(callback: => Unit): AsyncHttpClient = {
      new DefaultAsyncHttpClient() {
        override def close(): Unit = {
          super.close()
          callback
        }
      }
    }
  }

}
