package protocol

import java.net.InetAddress
import java.net.URI

import org.junit.runner.RunWith
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class UPnPExternalIPProviderSpec extends AnyFlatSpec with Matchers {
  "UPnPExternalIPProvider.controlLocation" should "read LOCATION header from an SSDP response" in {
    val response =
      "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=120\r\nLOCATION: http://192.168.1.1:1900/device.xml\r\nST: upnp\r\n\r\n"
    UPnPExternalIPProvider.controlLocation(response) shouldBe Some(new URI("http://192.168.1.1:1900/device.xml"))
  }

  "UPnPExternalIPProvider.controlUrlFromDeviceXml" should "extract WANIP control URL from device xml" in {
    val base = new URI("http://192.168.1.1:1900/device.xml")
    val deviceXml =
      <root>
        <serviceList>
          <service>
            <serviceType>urn:schemas-upnp-org:service:WANIPConnection:1</serviceType>
            <controlURL>/upnp/control/WANIPConn1</controlURL>
            <SCPDURL>/wanipconnSCPD.xml</SCPDURL>
          </service>
        </serviceList>
      </root>

    UPnPExternalIPProvider.controlUrlFromDeviceXml(base, deviceXml) shouldBe
      Some(new URI("http://192.168.1.1:1900/upnp/control/WANIPConn1"))
  }

  "UPnPExternalIPProvider.externalIpFromSoapResponse" should "extract external ip from soap xml" in {
    val soap =
      <Envelope>
        <Body>
          <GetExternalIPAddressResponse>
            <NewExternalIPAddress>203.0.113.7</NewExternalIPAddress>
          </GetExternalIPAddressResponse>
        </Body>
      </Envelope>

    UPnPExternalIPProvider.externalIpFromSoapResponse(soap) shouldBe Some(InetAddress.getByName("203.0.113.7"))
  }
}
