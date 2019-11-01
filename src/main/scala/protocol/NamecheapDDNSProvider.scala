package protocol

import java.util.logging.Logger

object NamecheapDNSProvider {

  val logger: Logger = Logger.getLogger(this.getClass.getName)

  val DdnsUrlPrefix: String = "https://dynamicdns.park-your-domain.com/update"

  def ddnsPassword(): String = {
    import app.Scant
    val config = Scant.configuration()
    config.getProperty("ddns.service.password")
  }
}

class NamecheapDDNSProvider extends DDNSProvider {

  import java.net.{HttpURLConnection,InetAddress,URL}

  import app.{Domain,Host}
  import protocol.NamecheapDNSProvider._

  override def update(host: Host, domain: Domain, address: InetAddress): Unit = {
    val password = ddnsPassword()

    val queryParams = s"host=$host&domain=$domain&password=$password&ip=${address.toString}"
    val ddnsUpdate = s"$DdnsUrlPrefix?$queryParams"

    val ddnsUrl= new URL(ddnsUpdate)
    val ddnsCon = ddnsUrl.openConnection().asInstanceOf[HttpURLConnection]
    ddnsCon.setRequestMethod("GET")

    logger.fine(s"HTTP response --> ${ddnsCon.getResponseCode} ${ddnsCon.getResponseMessage}")
  }
}
