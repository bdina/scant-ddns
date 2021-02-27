package protocol

import java.net.InetAddress

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.OptionValues._
import flatspec._
import matchers._
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UPnPExternalIPProviderSpec extends AnyFlatSpec with should.Matchers {
  "A UPnPExternalIPProvider" should "fetch an IP from a UPnP server" in {
    UPnPExternalIPProvider().address() shouldBe defined
  }
}
