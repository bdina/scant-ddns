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

  import java.net.{InetAddress,URI}

  import app.{Domain,Host}
  import protocol.NamecheapDNSProvider._

  import protocol.http._
  import protocol.Http.httpClient

  override def update(host: Host, domain: Domain, address: InetAddress): Unit = {
    val password = ddnsPassword()

    val queryParams = s"host=${host.name}&domain=${domain.name}&password=$password&ip=${address.getHostAddress}"
    val ddnsUpdate = s"$DdnsUrlPrefix?$queryParams"

    val ddnsUri = new URI(ddnsUpdate)
    val ddnsResponse = httpClient.tryGet(ddnsUri)

    logger.finer(s"HTTP query => $queryParams :: URL => $ddnsUpdate")
    logger.info(s"DDNS update response --> ${ddnsResponse}")
  }

  override def toString() = "NameCheap Dynamic DNS provider"
}
