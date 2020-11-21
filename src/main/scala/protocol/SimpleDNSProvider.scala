package protocol

import client.SimpleDnsClient
import java.net.InetAddress

import app.Scant

object SimpleDNSProvider {

  val DefaultServerAddress: String = "8.8.8.8"

  def dnsServer(): InetAddress =
    InetAddress.getByName(Scant.configuration().getProperty("dns.server.ip", DefaultServerAddress))
}

case class SimpleDNSProvider(val resolver: InetAddress = SimpleDNSProvider.dnsServer()) extends DNSProvider with app.ScantLogging {

  val dnsClient = SimpleDnsClient(resolver)
  logger.info(s"CREATED with resolver: $resolver")

  import app.{Domain,Host}
  override def address(host: Host, domain: Domain): Option[InetAddress] = dnsClient.address(host,domain)
}
