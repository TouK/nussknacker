package pl.touk.nussknacker.engine.assembly

import org.apache.commons.io.FileUtils
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.jar.{JarEntry, JarFile, JarOutputStream}
import scala.jdk.CollectionConverters.EnumerationHasAsScala
import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Using

class UberJarAssemblerTest extends AnyFunSuite with Matchers {

  test("builds uber jar with manifest and merged entries") {
    withTempDir { tempDir =>
      val jar1 = createJar(
        tempDir,
        "jar1.jar",
        List(
          "a.txt"      -> "A",
          "shared.txt" -> "identical"
        )
      )
      val jar2 = createJar(
        tempDir,
        "jar2.jar",
        List(
          "b.txt"      -> "B",
          "shared.txt" -> "identical"
        )
      )

      val outputJar = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
        .buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Files.exists(outputJar) shouldBe true
      outputJar.getFileName.toString should startWith("test-jar-")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val names = jarFile.entries().asScala.map(_.getName).toList
        names should contain("META-INF/MANIFEST.MF")
        names should contain("a.txt")
        names should contain("b.txt")
        names should contain("shared.txt")

        val manifest = jarFile.getManifest
        manifest.getMainAttributes.getValue("Main-Class") shouldBe "com.example.Main"
        manifest.getMainAttributes.getValue("Manifest-Version") shouldBe "1.0"
      }
    }
  }

  test("uber jar file name is deterministic for jars in given order") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("a.txt" -> "a", "b.txt" -> "b"))
      val jar2 = createJar(tempDir, "jar2.jar", List("c.txt" -> "c", "d.txt" -> "d"))

      val assembler  = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
      val outputJar1 = assembler.buildJar(tempDir, List(jar1, jar2), "com.example.Main")
      val outputJar2 = assembler.buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      outputJar1.getFileName.toString shouldBe outputJar2.getFileName.toString
    }
  }

  test("applies Deduplicate strategy by default - writes single entry when matching entries have identical content") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("entryA" -> "identitcal"))
      val jar2 = createJar(tempDir, "jar2.jar", List("entryA" -> "identitcal"))

      val outputJar = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
        .buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val names = jarFile.entries().asScala.map(_.getName).toList
        names.count(_ == "entryA") shouldBe 1
        readEntryText(jarFile, "entryA") shouldBe "identitcal"
      }
    }
  }

  test(
    "applies Deduplicate strategy by default - throws exception when applying Deduplicate merge strategy for multiple files with different content"
  ) {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("a" -> "v1"))
      val jar2 = createJar(tempDir, "jar2.jar", List("a" -> "v2"))

      val ex = intercept[RuntimeException] {
        new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
          .buildJar(tempDir, List(jar1, jar2), "com.example.Main")
      }

      ex.getMessage should fullyMatch regex "^Conflict: entry '.*' has different content in .* and .*$"
    }
  }

  test("applies First strategy rule - writes the first matching entry in order of input jars") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("conflict.txt" -> "v1"))
      val jar2 = createJar(tempDir, "jar2.jar", List("conflict.txt" -> "v2"))

      val assembler = new UberJarAssembler(
        mergeRules = List(MergeRule("conflict.txt", MergeStrategy.First)),
        "test-jar"
      )

      val outputJar = assembler.buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        readEntryText(jarFile, "conflict.txt") shouldBe "v1"
      }
    }
  }

  test("applies Discard strategy rule - discards all matching files") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("discard.txt" -> "v1", "keep.txt" -> "k1"))
      val jar2 = createJar(tempDir, "jar2.jar", List("discard.txt" -> "v2", "keep.txt" -> "k1"))

      val assembler = new UberJarAssembler(
        mergeRules = List(MergeRule("discard.txt", MergeStrategy.Discard)),
        "test-jar"
      )

      val outputJar = assembler.buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        jarFile.getEntry("discard.txt") shouldBe null
        readEntryText(jarFile, "keep.txt") shouldBe "k1"
      }
    }
  }

  test(
    "applies FilterDistinctLines strategy rule - writes a single entry with concatenated lines from matching entries"
  ) {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("lines.txt" -> "a\nb\n"))
      val jar2 = createJar(tempDir, "jar2.jar", List("lines.txt" -> "b\nc\n"))

      val assembler = new UberJarAssembler(
        mergeRules = List(MergeRule("lines.txt", MergeStrategy.FilterDistinctLines)),
        "test-jar"
      )

      val outputJar = assembler.buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        readEntryText(jarFile, "lines.txt") shouldBe
          """a
            |b
            |c""".stripMargin
      }
    }
  }

  test("applies Concat strategy rule - concatenates entries without duplicating newline") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("concat.txt" -> "part-1\n"))
      val jar2 = createJar(tempDir, "jar2.jar", List("concat.txt" -> "part-2\n"))

      val outputJar = new UberJarAssembler(
        mergeRules = List(MergeRule("concat.txt", MergeStrategy.Concat)),
        uberJarPrefix = "test-jar"
      )
        .buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        readEntryText(jarFile, "concat.txt") shouldBe "part-1\npart-2\n"
      }
    }
  }

  test("applies Concat strategy rule - concatenates entries and adds missing newlines") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("concat.txt" -> "part-1"))
      val jar2 = createJar(tempDir, "jar2.jar", List("concat.txt" -> "part-2"))

      val outputJar = new UberJarAssembler(
        mergeRules = List(MergeRule("concat.txt", MergeStrategy.Concat)),
        uberJarPrefix = "test-jar"
      )
        .buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        readEntryText(jarFile, "concat.txt") shouldBe "part-1\npart-2\n"
      }
    }
  }

  test("applies Rename strategy rule - appends source jar name to entry name") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("rename.txt" -> "r1"))

      val assembler = new UberJarAssembler(
        mergeRules = List(MergeRule("rename.txt", MergeStrategy.Rename)),
        "test-jar"
      )

      val outputJar = assembler.buildJar(tempDir, List(jar1), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val names = jarFile.entries().asScala.map(_.getName).toSet

        names should contain("rename.txt_jar1")
        names should not contain "rename.txt"
        readEntryText(jarFile, "rename.txt_jar1") shouldBe "r1"
      }
    }
  }

  test("applies FilterDistinctLines strategy to META-INF/services files by default") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("META-INF/services/com.example.Service" -> "impl.A\nimpl.B\n"))
      val jar2 = createJar(tempDir, "jar2.jar", List("META-INF/services/com.example.Service" -> "impl.B\nimpl.C\n"))

      val outputJar = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
        .buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val mergedText = readEntryText(jarFile, "META-INF/services/com.example.Service")
        mergedText shouldBe
          """impl.A
            |impl.B
            |impl.C""".stripMargin
      }
    }
  }

  test("applies Discard strategy to manifest and signature files by default") {
    withTempDir { tempDir =>
      val jar1 = createJar(
        tempDir,
        "jar1.jar",
        List(
          "META-INF/MANIFEST.MF" -> "m",
          "META-INF/TEST.SF"     -> "s",
          "META-INF/TEST.RSA"    -> "s",
          "META-INF/TEST.DSA"    -> "s",
          "file.txt"             -> "ok"
        )
      )

      val outputJar = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
        .buildJar(tempDir, List(jar1), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val names = jarFile.entries().asScala.map(_.getName).toList

        names.count(_ == "META-INF/MANIFEST.MF") shouldBe 1
        names should not contain "META-INF/TEST.SF"
        names should not contain "META-INF/TEST.RSA"
        names should not contain "META-INF/TEST.DSA"
        names should contain("file.txt")
      }
    }
  }

  test("renames license and readme files by default") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("META-INF/LICENSE" -> "l1", "docs/README" -> "r1"))
      val jar2 = createJar(tempDir, "jar2.jar", List("META-INF/LICENSE" -> "l2", "docs/README" -> "r2"))

      val outputJar = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
        .buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val names = jarFile.entries().asScala.map(_.getName).toSet

        names should contain("META-INF/LICENSE_jar1")
        names should contain("META-INF/LICENSE_jar2")
        names should contain("docs/README_jar1")
        names should contain("docs/README_jar2")
      }
    }
  }

  test("does not rename class files with readme and license typical names") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("LICENSE.class" -> "a"))
      val jar2 = createJar(tempDir, "jar2.jar", List("README.class" -> "a"))

      val outputJar = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
        .buildJar(tempDir, List(jar1, jar2), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val names = jarFile.entries().asScala.map(_.getName).toSet

        names should contain("LICENSE.class")
        names should contain("README.class")
      }
    }
  }

  test("discards JPMS module descriptors by default") {
    withTempDir { tempDir =>
      val jar1 = createJar(
        tempDir,
        "jar1.jar",
        List(
          "module-info.class"                     -> "module a {}",
          "META-INF/versions/x/module-info.class" -> "module a {}"
        )
      )

      val outputJar = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")
        .buildJar(tempDir, List(jar1), "com.example.Main")

      Using.resource(new JarFile(outputJar.toFile)) { jarFile =>
        val names = jarFile.entries().asScala.map(_.getName).toSet
        names should not contain "module-info.class"
        names should not contain "META-INF/versions/x/module-info.class"
      }
    }
  }

  test("removes temporary jar file when assembly fails") {
    withTempDir { tempDir =>
      val jar1 = createJar(tempDir, "jar1.jar", List("a" -> "v1"))
      val jar2 = createJar(tempDir, "jar2.jar", List("a" -> "v2"))

      val assembler = new UberJarAssembler(mergeRules = List.empty, uberJarPrefix = "test-jar")

      intercept[RuntimeException] {
        assembler.buildJar(tempDir, List(jar1, jar2), "com.example.Main")
      }

      val jarTempFiles = Using.resource(Files.newDirectoryStream(tempDir, "test-jar*.jar.tmp")) { dirStream =>
        dirStream.iterator().asScala.toList
      }

      jarTempFiles shouldBe empty
    }
  }

  private def createJar(tempDir: Path, jarName: String, entries: List[(String, String)]): Path = {
    val path = tempDir.resolve(jarName)
    Using.resource(new JarOutputStream(Files.newOutputStream(path))) { jos =>
      entries.foreach { case (name, content) =>
        val entry = new JarEntry(name)
        jos.putNextEntry(entry)
        jos.write(content.getBytes(StandardCharsets.UTF_8))
        jos.closeEntry()
      }
    }
    path
  }

  private def readEntryText(jarFile: JarFile, entryName: String): String = {
    val entry = jarFile.getEntry(entryName)
    Using.resource(jarFile.getInputStream(entry)) { is =>
      new String(is.readAllBytes(), StandardCharsets.UTF_8)
    }
  }

  private def withTempDir(testBody: Path => Any): Unit = {
    val tempDir = Files.createTempDirectory("uber-jar-test-")
    try {
      testBody(tempDir)
    } finally {
      FileUtils.deleteQuietly(tempDir.toFile)
    }
  }

}
