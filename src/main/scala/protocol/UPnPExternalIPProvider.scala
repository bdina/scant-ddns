package protocol

import java.net.{InetAddress,URI}

object UPnPExternalIPProvider extends app.ScantLogging {
  import app.Scant

  val SsdpAddr: InetAddress = InetAddress.getByName("239.255.255.250")
  val SsdpPort: Int = 1900
  val SsdpMx: Int = 2
  val SsdpSt: String = "urn:schemas-upnp-org:device:InternetGatewayDevice:1"

  val ssdpRequest: String =
    s"M-SEARCH * HTTP/1.1\r\nHOST: $SsdpAddr:$SsdpPort\r\nMAN: 'ssdp:discover'\r\nMX: $SsdpMx\r\nST: $SsdpSt\r\n"

  val serviceNs: String = "urn:schemas-upnp-org:service:WANIPConnection:1"
  val soapAction: String = "urn:schemas-upnp-org:service:WANIPConnection:1#GetExternalIPAddress"

  val soapHeaders: Map[String,String] = Map("SOAPAction" -> soapAction)
  val soapBody: xml.Elem =
    <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope"
     SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
      <SOAP-ENV:Body>
        { serviceNs }
      </SOAP-ENV:Body>
    </SOAP-ENV:Envelope>

  def routerAddress(): InetAddress = InetAddress.getByName(Scant.configuration().getProperty("router.ip"))

  import scala.util.Try
  import scala.util.matching.Regex
  import scala.xml.XML

  import protocol.http._
  import protocol.Http.httpClient

  def getXML(uri: URI): Try[xml.Elem] = httpClient.tryGet(uri).flatMap { case resp =>
    logger.finest(s"fetched from URL (GET) => $resp")
    Try(XML.loadString(resp))
  }

  def postXML(uri: URI, body: xml.Elem, headers: Map[String,String]): Try[xml.Elem] =
    httpClient.tryPost(uri, body.toString, headers).flatMap { case resp =>
      logger.finest(s"fetched from URL (POST) => $resp")
      Try(XML.loadString(resp))
    }

  val Location: Regex = """.*\r\nLOCATION: (?<value>.*?)\r\n""".r
  def controlLocation(ssdpResponse: String): Option[URI] =
    Location.findFirstMatchIn(ssdpResponse).map { case m => new URI(m.group("value")) }

  def fetchExternalIp(url: URI): Option[InetAddress] = getXML(url).map { case xml =>
    val baseUrl = s"http://${url.getHost}:${url.getPort}"

    val ctrl_url: Option[URI] = (xml \\ "service").find { case svc =>
      val serviceType = (svc \ "serviceType").text
      logger.fine(s"$serviceType")
      serviceType == serviceNs
    }.map { case svc =>
      val controlUrl = s"$baseUrl${ (svc \ "controlURL").text }"
      val scpdUrl    = s"$baseUrl${ (svc \ "SCPDURL").text }"
      logger.fine(s"SCPD_URL: $scpdUrl\n  CTRL_URL: $controlUrl")
      new URI(controlUrl)
    }

    ctrl_url.flatMap { case ctrlUrl =>
      postXML(ctrlUrl, soapBody, soapHeaders).map { case ctrlContent =>
        val parsed = (ctrlContent \\ "NewExternalIPAddress").text
        logger.fine(s"external IP address => $parsed")
        InetAddress.getByName(parsed)
      }.toOption
    }
  }.toOption.flatten

  val SO_TIMEOUT = java.util.concurrent.TimeUnit.SECONDS.toMillis(1).toInt
}

case class UPnPExternalIPProvider() extends ExternalIPProvider with app.ScantLogging {
  import UPnPExternalIPProvider._

  import java.net.{DatagramPacket, DatagramSocket}
  import scala.util.Using

  import protocol.net._

  override def address(): Option[InetAddress] = {
    val ssdpRequestBytes = ssdpRequest.getBytes

    val request =
      new DatagramPacket(ssdpRequestBytes, 0, ssdpRequestBytes.length, SsdpAddr, SsdpPort)

    Using(new DatagramSocket()) { case socket =>
      (for {
        _ <- socket.trySend(request).toOption
        packet <- socket.tryReceive(bytes=8192).toOption
        response = new String(packet.getData).trim
      } yield {
        response
      }).flatMap { case data =>
        controlLocation(data).flatMap { case url =>
          logger.info(s"control location url => $url")
          fetchExternalIp(url)
        }.map { case address =>
          logger.info(s"external address => ${address.getHostAddress}")
          address
        }
      }
    }.getOrElse(None)
  }
}
