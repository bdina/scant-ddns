package protocol

import java.net.InetAddress

import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.OptionValues._
import flatspec._
import matchers._
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class SimpleDNSProviderSpec extends AnyFlatSpec with should.Matchers {
  "A SimpleDNSProvider" should "fetch an IP from a UPnP server" in {
    import app.{Domain,Host}
    SimpleDNSProvider().address(Host("www"),Domain("google.com")) shouldBe defined
  }
}
