package pl.touk.nussknacker.engine.flink.util.sharedservice

import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}

import java.util.concurrent.atomic.AtomicBoolean

class SharedServiceSpec extends FunSuite with Matchers {

  private implicit val metaData: MetaData = MetaData("test1", StreamMetaData())

  class TestSharedService(val creationData: String) extends SharedService[String] {

    val isClosed = new AtomicBoolean(false)

    override protected def sharedServiceHolder: SharedServiceHolder[String, _] = TestSharedServiceHolder

    override protected[sharedservice] def internalClose(): Unit = {
      isClosed.set(true)
    }

  }

  object TestSharedServiceHolder extends SharedServiceHolder[String, TestSharedService] {
    override protected def createService(config: String, metaData: MetaData): TestSharedService = new TestSharedService(config)
  }

  test("should returned cached instance") {
    val first::others = (1 to 10).par.map(_ => TestSharedServiceHolder.retrieveService("test1")).toList
    others.foreach { service =>
      //we test reference equality here!
      first eq service shouldBe true
    }
  }


  test("should returned different instance for different creation data") {

    val one = TestSharedServiceHolder.retrieveService("oneValue")
    val two = TestSharedServiceHolder.retrieveService("secondValue")

    one.creationData shouldBe "oneValue"
    two.creationData shouldBe "secondValue"
    one ne two shouldBe true

  }

  test("should close only after all instances close") {

    val data = "closing"

    val total = 100
    (1 to total).par.foreach(_ => TestSharedServiceHolder.retrieveService(data))
    val oneMore = TestSharedServiceHolder.retrieveService(data)
    oneMore.isClosed.get() shouldBe false

    (1 to total).par.foreach(_ => TestSharedServiceHolder.returnService(data))
    oneMore.isClosed.get() shouldBe false
    TestSharedServiceHolder.returnService(data)
    oneMore.isClosed.get() shouldBe true
  }

}
