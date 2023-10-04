/**
  *
[info] # Warmup Iteration   1: 89,268 ms/op
[info] # Warmup Iteration   2: 3,493 ms/op
[info] # Warmup Iteration   3: 4,596 ms/op
[info] Iteration   1: 2,266 ms/op
[info] Iteration   2: 2,479 ms/op
[info] Iteration   3: 2,513 ms/op
[info] Iteration   4: 2,204 ms/op
[info] Iteration   5: 2,637 ms/op
[info] Iteration   6: 2,444 ms/op
[info] Iteration   7: 2,520 ms/op
[info] Iteration   8: 2,216 ms/op
[info] Result "pl.touk.nussknacker.engine.benchmarks.suggester.ExpressionSuggesterBenchmark.chainCallBenchmark":
[info]   N = 8
[info]   mean =      2,410 ±(99.9%) 0,307 ms/op

[info] # Warmup Iteration   1: 830,946 ms/op
[info] # Warmup Iteration   2: 5,211 ms/op
[info] # Warmup Iteration   3: 6,217 ms/op
[info] Iteration   1: 4,006 ms/op
[info] Iteration   2: 4,358 ms/op
[info] Iteration   3: 4,247 ms/op
[info] Iteration   4: 4,500 ms/op
[info] Iteration   5: 4,390 ms/op
[info] Iteration   6: 5,184 ms/op
[info] Iteration   7: 4,331 ms/op
[info] Iteration   8: 3,481 ms/op
[info] Result "pl.touk.nussknacker.engine.benchmarks.suggester.ExpressionSuggesterBenchmark.complexExpressionBenchmark":
[info]   N = 8
[info]   mean =      4,312 ±(99.9%) 0,912 ms/op
  */

package pl.touk.nussknacker.engine.benchmarks.suggester

import org.openjdk.jmh.annotations._

import java.util.concurrent.TimeUnit

@State(Scope.Thread)
class ExpressionSuggesterBenchmark {

  private val setup = new ExpressionSuggesterBenchmarkSetup()

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def chainCallBenchmark(): AnyRef = {
    setup.test("(#foo.bar.foo.bar.foo.toString + #bar.foo.bar.toString).toString.toUpperCase.t", 78)
  }

  @Benchmark
  @BenchmarkMode(Array(Mode.SingleShotTime))
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  def complexExpressionBenchmark(): AnyRef = {
    setup.test(
      """{{"abc": 1, "def": "foo"}, {"abc":-4, "def": "bar"}, {"abc": #intVar, "def": #stringVar}}.?[#this.abc > 0].![#this.def][0].toUpperCase.""",
      135
    )
  }

}
