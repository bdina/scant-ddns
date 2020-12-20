package protocol

object NamecheapDNSProvider extends app.ScantLogging {

  val DdnsUrlPrefix: String = "https://dynamicdns.park-your-domain.com/update"

  def ddnsPassword(): String = {
    import app.Scant
    val config = Scant.configuration()
    config.getProperty("ddns.service.password")
  }
}

case class NamecheapDDNSProvider() extends DDNSProvider {

  import java.net.{HttpURLConnection,InetAddress,URL}

  import app.{Domain,Host}
  import protocol.NamecheapDNSProvider._

  override def update(host: Host, domain: Domain, address: InetAddress): Unit = {
    val password = ddnsPassword()

    val queryParams = s"host=${host.name}&domain=${domain.name}&password=$password&ip=${address.getHostAddress}"
    val ddnsUpdate = s"$DdnsUrlPrefix?$queryParams"

    val ddnsUrl = new URL(ddnsUpdate)
    val ddnsConn = ddnsUrl.openConnection().asInstanceOf[HttpURLConnection]
    ddnsConn.setRequestMethod("GET")

    logger.finer(s"HTTP query => $queryParams :: URL => $ddnsUpdate :: response --> ${ddnsConn.getResponseCode} ${ddnsConn.getResponseMessage}")
  }

  override def toString() = "NameCheap Dynamic DNS provider"
}
