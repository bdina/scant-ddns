package protocol

import java.io.{BufferedReader,DataOutputStream,InputStreamReader}
import java.net.{InetAddress,HttpURLConnection,URL}

object UPnPExternalIPProvider extends app.ScantLogging {

  import app.Scant

  val SsdpAddr: InetAddress = InetAddress.getByName("239.255.255.250")
  val SsdpPort: Integer = 1900
  val SsdpMx: Integer = 2
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

  def routerIp(): InetAddress = InetAddress.getByName(Scant.configuration().getProperty("router.ip"))

  def httpGet(url: URL): String = {
    val con = url.openConnection().asInstanceOf[HttpURLConnection]
    con.setRequestMethod("GET")

    val in = new BufferedReader(new InputStreamReader(con.getInputStream))
    val content = new StringBuffer()
    var inputLine = in.readLine()
    do {
      content.append(inputLine)
      inputLine = in.readLine()
    } while (inputLine != null)
    in.close()
    content.toString
  }

  def httpPost(url: URL, body: String, headers: List[(String,String)]): String = {
    val con = url.openConnection().asInstanceOf[HttpURLConnection]
    con.setRequestMethod("POST")

    con.setDoOutput(true)
    headers.foreach{ h => con.setRequestProperty(h._1, h._2) }
    val wr = new DataOutputStream(con.getOutputStream)
    wr.writeBytes(body)
    wr.flush()
    wr.close()

    val in = new BufferedReader(new InputStreamReader(con.getInputStream))
    val content = new StringBuffer()
    var inputLine = in.readLine()
    do {
      content.append(inputLine)
      inputLine = in.readLine()
    } while (inputLine != null)
    in.close()
    content.toString
  }

  def controlLocation(ssdpResponse: String): Option[String] = {

    import java.util.regex.Pattern

    val parsed = Pattern.compile("LOCATION: (?<value>.*?)\r\n").matcher(ssdpResponse)
    if ( parsed.find ) {
      Some(parsed.group(1))
    } else {
      logger.severe("unable to find control location on network!")
      None
    }
  }

  val WAN_IP_URN = "urn:schemas-upnp-org:service:WANIPConnection:1"

  def fetchExternalIp(url: URL): Option[InetAddress] = {

    import scala.xml.XML

    val xml = XML.loadString(httpGet(url))
    val baseUrl = s"http://${url.getHost}:${url.getPort}"

    var ctl_url: Option[URL] = None
    (xml \\ "service").foreach(svc => {
        val serviceType = (svc \ "serviceType").text
        val controlUrl  = s"$baseUrl${ (svc \ "controlURL").text }"
        val scpdUrl     = s"$baseUrl${ (svc \ "SCPDURL").text }"
        if ( WAN_IP_URN.equals(serviceType) ) {
          ctl_url = Some(new URL(controlUrl))
          logger.fine(s"found WAN IP control url: $ctl_url")
        }
        logger.fine(s"$serviceType:\n  SCPD_URL: $scpdUrl\n  CTRL_URL: $controlUrl")
    })

    ctl_url match {
      case Some(ctlUrl) =>
        val headers = ("SOAPAction", soapAction) :: Nil
        val ctrlContent = httpPost(ctlUrl, soapBody, headers)

        val ctrlXml = XML.loadString(ctrlContent.toString)
        Some(InetAddress.getByName((ctrlXml \\ "NewExternalIPAddress").text))
      case None => None
    }
  }
}

case class UPnPExternalIPProvider() extends ExternalIPProvider with app.ScantLogging {

  import java.net.{DatagramPacket, DatagramSocket}

  import UPnPExternalIPProvider._

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

    controlLocation(responseData) match {
      case Some(location) =>
        val url = new URL(location)
        import scala.util.{Failure,Success,Try}
        Try(fetchExternalIp(url)) match {
          case Success(externalIp) =>
            logger.info(s"external ip - ${externalIp.getOrElse("none")}")
            socket.close()
            externalIp
          case Failure(ex) =>
            logger.severe(s"no external ip discovered! ${ex.getMessage}")
            socket.close()
            None
        }
      case None =>
        logger.severe("no external ip discovered!")
        socket.close()
        None
    }
  }
}
