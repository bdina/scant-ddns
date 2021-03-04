package protocol

import java.net.{InetAddress,URI}

object UPnPExternalIPProvider extends app.ScantLogging {
  import app.Scant

  val SsdpAddr: InetAddress = InetAddress.getByName("239.255.255.250")
  val SsdpPort: Int = 1900
  val SsdpMx: Int = 2
  val SsdpSt: String = "urn:schemas-upnp-org:device:InternetGatewayDevice:1"

  val ssdpRequest: String = s"M-SEARCH * HTTP/1.1\r\nHOST: $SsdpAddr:$SsdpPort\r\nMAN: 'ssdp:discover'\r\nMX: $SsdpMx\r\nST: $SsdpSt\r\n"

  val soapEncoding: String = "http://schemas.xmlsoap.org/soap/encoding/"
  val soapEnv: String = "http://schemas.xmlsoap.org/soap/envelope"
  val serviceNs: String = "urn:schemas-upnp-org:service:WANIPConnection:1"

  val soapBody: String = s"""<?xml version="1.0"?>
                            |<SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope" SOAP-ENV:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                            |  <SOAP-ENV:Body>
                            |    $serviceNs
                            |  </SOAP-ENV:Body>
                            |</SOAP-ENV:Envelope>""".stripMargin

  val soapAction: String = "urn:schemas-upnp-org:service:WANIPConnection:1#GetExternalIPAddress"

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

  def postXML(uri: URI, body: String, headers: Map[String,String]): Try[xml.Elem] =
    httpClient.tryPost(uri, body, headers).flatMap { case resp =>
      logger.finest(s"fetched from URL (POST) => $resp")
      Try(XML.loadString(resp))
    }

  val Location: Regex = """.*\r\nLOCATION: (?<value>.*?)\r\n""".r
  def controlLocation(ssdpResponse: String): Option[URI] =
    Location.findFirstMatchIn(ssdpResponse).map { case m => new URI(m.group("value")) }

  val WAN_IP_URN = "urn:schemas-upnp-org:service:WANIPConnection:1"

  def fetchExternalIp(url: URI): Option[InetAddress] = getXML(url).map { case xml =>
    val baseUrl = s"http://${url.getHost}:${url.getPort}"

    var ctrl_url: Option[URI] = None
    (xml \\ "service").foreach(svc => {
        val serviceType = (svc \ "serviceType").text
        val controlUrl  = s"$baseUrl${ (svc \ "controlURL").text }"
        val scpdUrl     = s"$baseUrl${ (svc \ "SCPDURL").text }"
        if (serviceType == WAN_IP_URN) {
          ctrl_url = Some(new URI(controlUrl))
          logger.fine(s"found WAN IP control url: $ctrl_url")
        }
        logger.fine(s"$serviceType:\n  SCPD_URL: $scpdUrl\n  CTRL_URL: $controlUrl")
    })

    ctrl_url.flatMap { case ctrlUrl =>
      val headers = Map("SOAPAction" -> soapAction)
      postXML(ctrlUrl, soapBody, headers).map { case ctrlContent =>
        val parsed = (ctrlContent \\ "NewExternalIPAddress").text
        logger.info(s"external IP address => $parsed")
        InetAddress.getByName(parsed)
      }.toOption
    }
  }.toOption.flatten
}

case class UPnPExternalIPProvider() extends ExternalIPProvider with app.ScantLogging {
  import UPnPExternalIPProvider._

  import java.net.{DatagramPacket, DatagramSocket}

  override def address(): Option[InetAddress] = {
    val ssdpRequestBytes = ssdpRequest.getBytes

    val request =
      new DatagramPacket(ssdpRequestBytes, 0, ssdpRequestBytes.length, SsdpAddr, SsdpPort)

    val socket = new DatagramSocket()
    socket.setSoTimeout(1000)

    val buff = new Array[Byte](8192)
    val response = new DatagramPacket(buff, buff.length)

    socket.send(request)
    socket.receive(response)

    val responseData = new String(response.getData).trim
    socket.close()

    controlLocation(responseData).flatMap { case url =>
      logger.finer(s"found control location => $url")
      fetchExternalIp(url)
    }
  }
}
