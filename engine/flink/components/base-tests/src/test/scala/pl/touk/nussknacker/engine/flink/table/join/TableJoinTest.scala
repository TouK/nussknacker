package pl.touk.nussknacker.engine.flink.table.join

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.RuntimeExecutionMode
import org.apache.flink.api.connector.source.Boundedness
import org.scalatest.{Inside, LoneElement}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.build.{GraphBuilder, ScenarioBuilder}
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider
import pl.touk.nussknacker.engine.flink.table.join.TableJoinTest.OrderOrProduct
import pl.touk.nussknacker.engine.flink.test.FlinkSpec
import pl.touk.nussknacker.engine.flink.util.transformer.join.BranchType
import pl.touk.nussknacker.engine.graph.expression.Expression
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

  private val mainBranchId   = "main"
  private val joinedBranchId = "joined"

  private val joinNodeId = "joined-node-id"

  private val someProduct    = OrderOrProduct.createProduct(1, "Foo product")
  private val anotherProduct = OrderOrProduct.createProduct(2, "Bar product")
  private val delayedProduct = OrderOrProduct.createProduct(3, "Delayed product")

  private val orderReferringToExistingProduct = OrderOrProduct.createOrder(10, someProduct.id)

  private val nonExistingProductId               = 100
  private val orderReferringToNonExistingProduct = OrderOrProduct.createOrder(20, nonExistingProductId)

  private val orderReferringToDelayedProduct = OrderOrProduct.createOrder(30, delayedProduct.id)

  test("should inner join stream") {
    val enrichedOrders = runner.runWithData[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(JoinType.INNER),
      List(
        someProduct,
        anotherProduct,
        orderReferringToExistingProduct,
        orderReferringToNonExistingProduct,
        orderReferringToDelayedProduct,
        delayedProduct
      ),
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava,
      Map(
        "orderId" -> orderReferringToDelayedProduct.id,
        "product" -> Map(
          "id"   -> delayedProduct.id,
          "name" -> delayedProduct.name
        ).asJava
      ).asJava
    )
  }

  test("should outer join stream") {
    val enrichedOrders = runner.runWithData[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(JoinType.OUTER),
      List(
        someProduct,
        anotherProduct,
        orderReferringToExistingProduct,
        orderReferringToNonExistingProduct,
        orderReferringToDelayedProduct,
        delayedProduct
      ),
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava,
      Map(
        "orderId" -> orderReferringToNonExistingProduct.id,
        "product" -> Map(
          "id"   -> null,
          "name" -> null
        ).asJava
      ).asJava,
      Map(
        "orderId" -> orderReferringToDelayedProduct.id,
        "product" -> Map(
          "id"   -> delayedProduct.id,
          "name" -> delayedProduct.name
        ).asJava
      ).asJava
    )
  }

  test("should allow to use different types that can be compared in key expressions") {
    val enrichedOrders = runner.runWithData[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(
        JoinType.INNER,
        orderKeyExpression = "#input.productId".spel,
        productKeyExpression = "#input.id.longValue".spel
      ),
      List(
        someProduct,
        orderReferringToExistingProduct
      ),
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )

    enrichedOrders.validValue.errors shouldBe empty
    enrichedOrders.validValue.successes shouldEqual List(
      Map(
        "orderId" -> orderReferringToExistingProduct.id,
        "product" -> Map(
          "id"   -> someProduct.id,
          "name" -> someProduct.name
        ).asJava
      ).asJava
    )
  }

  test("shouldn't allow to use types that can't be compared in key expressions") {
    val enrichedOrders = runner.runWithData[OrderOrProduct, java.util.Map[String, AnyRef]](
      prepareJoiningScenario(
        JoinType.INNER,
        orderKeyExpression = "#input.productId".spel,
        productKeyExpression = "#input.id.toString".spel
      ),
      List(orderReferringToNonExistingProduct),
      Boundedness.BOUNDED,
      Some(RuntimeExecutionMode.BATCH)
    )

    enrichedOrders.invalidValue.toList should matchPattern {
      case CustomNodeError(
            `joinNodeId`,
            "Types Integer and String are not comparable",
            Some(ParameterName("Key"))
          ) :: Nil =>
    }
  }

  private def prepareJoiningScenario(
      joinType: JoinType,
      orderKeyExpression: Expression = "#input.productId".spel,
      productKeyExpression: Expression = "#input.id".spel
  ) = ScenarioBuilder
    .streaming(classOf[TableJoinTest].getSimpleName)
    .sources(
      GraphBuilder
        .source("orders-source", TestScenarioRunner.testDataSource)
        .filter("orders-filter", "#input.type == 'order'".spel)
        .branchEnd(mainBranchId, joinNodeId),
      GraphBuilder
        .source("products-source", TestScenarioRunner.testDataSource)
        .filter("product-filter", "#input.type == 'product'".spel)
        .branchEnd(joinedBranchId, joinNodeId),
      GraphBuilder
        .join(
          joinNodeId,
          "join",
          Some("product"),
          List(
            mainBranchId -> List(
              "Branch Type" -> s"T(${classOf[BranchType].getName}).${BranchType.MAIN}".spel,
              "Key"         -> orderKeyExpression
            ),
            joinedBranchId -> List(
              "Branch Type" -> s"T(${classOf[BranchType].getName}).${BranchType.JOINED}".spel,
              "Key"         -> productKeyExpression
            )
          ),
          "Join Type" -> s"T(${classOf[JoinType].getName}).$joinType".spel,
          "Output"    -> "#input".spel,
        )
        .emptySink(
          "end",
          TestScenarioRunner.testResultSink,
          "value" ->
            """{
              |  orderId: #input.id,
              |  product: {
              |   id: #product?.id,
              |   name: #product?.name
              |  }
              |}""".stripMargin.spel
        )
    )

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
