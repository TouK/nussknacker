package pl.touk.nussknacker.test

import scala.util.Random

object RandomImplicits {

  implicit class RandomExt(rand: Random) {

    private val AllowedStringLetters = ('a' to 'z') ++ ('A' to 'Z')

    private val MinStringLength = 4

    private val MaxStringLength = 32

    def nextString(): String =
      randomString(MinStringLength + rand.nextInt(MaxStringLength - MinStringLength))

    def randomString(length: Int): String =
      (0 until length).map(_ => AllowedStringLetters(rand.nextInt(AllowedStringLetters.length))).mkString

  }

}
