package pl.touk.nussknacker.engine.flink.table.join

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import org.scalatest.{Inside, LoneElement}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.join.TableJoinTest.OrderOrProduct
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.transformer.join.BranchType
import pl.touk.nussknacker.engine.util.test.TestScenarioRunner
import pl.touk.nussknacker.test.ValidatedValuesDetailedMessage

import scala.beans.BeanProperty

class TableJoinTest
    extends AnyFunSuite
    with FlinkSpec
    with Matchers
    with Inside
    with ValidatedValuesDetailedMessage
    with LoneElement {

  import pl.touk.nussknacker.engine.flink.util.test.FlinkTestScenarioRunner._
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private lazy val additionalComponents: List[ComponentDefinition] =
    FlinkTableComponentProvider.configIndependentComponents ::: Nil

  private lazy val runner = TestScenarioRunner
    .flinkBased(ConfigFactory.empty(), flinkMiniCluster)
    .withExtraComponents(additionalComponents)
    .build()

  private val MainBranchId = "main"

  private val JoinedBranchId = "joined"

  private val JoinNodeId = "joined-node-id"

  test("should be able to join") {
    val scenario = ScenarioBuilder
      .streaming("sample-join-last")
      .sources(
        GraphBuilder
          .source("orders-source", TestScenarioRunner.testDataSource)
          .filter("orders-filter", "#input.type == 'order'".spel)
          .branchEnd(MainBranchId, JoinNodeId),
        GraphBuilder
          .source("products-source", TestScenarioRunner.testDataSource)
          .filter("product-filter", "#input.type == 'product'".spel)
          .branchEnd(JoinedBranchId, JoinNodeId),
        GraphBuilder
          .join(
            JoinNodeId,
            "join",
            Some("product"),
            List(
              MainBranchId -> List(
                "branchType" -> s"T(${classOf[BranchType].getName}).MAIN".spel,
                "key"        -> s"#input.productId.toString".spel
              ),
              JoinedBranchId -> List(
                "branchType" -> s"T(${classOf[BranchType].getName}).JOINED".spel,
                "key"        -> s"#input.id.toString".spel
              )
            ),
            "output" -> "#input".spel,
          )
          .emptySink(
            "end",
            TestScenarioRunner.testResultSink,
            "value" ->
              """{
                |  orderId: #input.id,
                |  product: {
                |   id: #product.id,
                |   name: #product.name
                |  }
                |}""".stripMargin.spel
          )
      )

    val productId = 1
    val orderId   = 10
    val enrichedOrders = runner.runWithData[OrderOrProduct, java.util.Map[String, AnyRef]](
      scenario,
      List(
        OrderOrProduct.createProduct(productId, "Foo product"),
        OrderOrProduct.createOrder(orderId, productId),
      ),
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )

    val expectedEnrichedOrder = Map(
      "orderId" -> orderId,
      "product" -> Map(
        "id"   -> productId,
        "name" -> "Foo product"
      ).asJava
    ).asJava
    enrichedOrders.validValue.successes.loneElement shouldEqual expectedEnrichedOrder
  }

}

object TableJoinTest {

  // TODO: split into separate classes and pass two streams to separate source nodes
  // productId is dedicated only for order events
  // name is dedicated only for order events
  // It have to by POJO in order by acceptable by table api operators
  class OrderOrProduct(
      @BeanProperty var `type`: String,
      @BeanProperty var id: Int,
      @BeanProperty var name: String,
      @BeanProperty var productId: Int
  ) {

    def this() = this(null, -1, null, -1)

  }

  object OrderOrProduct {

    def createOrder(id: Int, productId: Int): OrderOrProduct = {
      new OrderOrProduct("order", id, null, productId)
    }

    def createProduct(id: Int, name: String): OrderOrProduct = {
      new OrderOrProduct("product", id, name, -1)
    }

  }

}
