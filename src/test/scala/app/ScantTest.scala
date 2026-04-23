package app

import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ScantSpec extends AnyFlatSpec with Matchers {
  "Scant.configuration" should "load configuration once and return cached properties" in {
    val first = Scant.configuration()
    val second = Scant.configuration()

    (first eq second) shouldBe true
  }
}
