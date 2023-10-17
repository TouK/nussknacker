package pl.touk.nussknacker.ui.api.helpers

//Representation of categories from designer.conf
object TestCategories {
  val Category1      = "Category1"
  val Category2      = "Category2"
  val TestCat        = "TESTCAT"
  val TestCat2       = "TESTCAT2"
  val ReqRes         = "ReqRes"
  val SecretCategory = "Secret"
  val UserCategory   = "UserCategory1"

  val CategoryCategories: List[String] = List(Category1, Category2)
  val TestCategories: List[String]     = List(TestCat, TestCat2)
  val ReqResCategories: List[String]   = List(ReqRes)

  val AllCategories: List[String] = CategoryCategories ++ TestCategories ++ ReqResCategories
}
