package protocol

import app.{Domain,Host}
import client.SimpleDnsClient

import java.net.InetAddress

class OpenDNSExternalIPProvider extends ExternalIPProvider {

  val dnsClient = new SimpleDnsClient(InetAddress.getByName("resolver1.opendns.com"))

  override def address(): Option[InetAddress] = dnsClient.address(Host("myip"), Domain("opendns.com"))
}
