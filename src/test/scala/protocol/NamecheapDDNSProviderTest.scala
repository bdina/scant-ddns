package protocol

import app.network.{Domain, Host}
import java.net.InetAddress
import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class NamecheapDDNSProviderSpec extends AnyFlatSpec with Matchers {
  "NamecheapDNSProvider.updateUri" should "build the expected ddns update URL" in {
    val uri = NamecheapDNSProvider.updateUri(
      host = Host("www"),
      domain = Domain("example.com"),
      password = "token",
      address = InetAddress.getByName("198.51.100.9")
    )

    uri.toString shouldBe
      "https://dynamicdns.park-your-domain.com/update?host=www&domain=example.com&password=token&ip=198.51.100.9"
  }
}
