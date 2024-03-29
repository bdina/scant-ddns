package protocol

import app.network.{Domain,Host}
import client.SimpleDnsClient

import java.net.InetAddress

case class OpenDNSExternalIPProvider() extends ExternalIPProvider with app.ScantLogging {

  val resolver = "resolver1.opendns.com"
  val dnsClient = SimpleDnsClient(InetAddress.getByName(resolver))
  logger.info(s"CREATED using resolver: $resolver")

  override def address(): Option[InetAddress] = dnsClient.query(Host("myip"), Domain("opendns.com"))
}
