package protocol

object NamecheapDNSProvider extends app.ScantLogging {

  val DdnsUrlPrefix: String = "https://dynamicdns.park-your-domain.com/update"

  def ddnsPassword(): String = {
    import app.Scant
    val config = Scant.configuration()
    config.getProperty("ddns.service.password")
  }

  import java.net.{InetAddress, URI}
  import app.network.{Domain, Host}

  def updateUri(host: Host, domain: Domain, password: String, address: InetAddress): URI = {
    val queryParams = s"host=${host}&domain=${domain}&password=$password&ip=${address.getHostAddress}"
    new URI(s"$DdnsUrlPrefix?$queryParams")
  }
}

case class NamecheapDDNSProvider() extends DDNSProvider {

  import java.net.InetAddress

  import app.network.{Domain,Host}
  import protocol.NamecheapDNSProvider._

  import protocol.http._
  import protocol.Http.httpClient

  override def update(host: Host, domain: Domain, address: InetAddress): Unit = {
    val password = ddnsPassword()
    val ddnsUri = updateUri(host, domain, password, address)
    val ddnsResponse = httpClient.tryGet(ddnsUri)

    logger.finer(s"HTTP query => ${ddnsUri.getQuery} :: URL => $ddnsUri")
    logger.info(s"DDNS update response --> ${ddnsResponse}")
  }

  override def toString() = "NameCheap Dynamic DNS provider"
}
