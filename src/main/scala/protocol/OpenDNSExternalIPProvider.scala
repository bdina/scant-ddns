package protocol

import client.SimpleDnsClient

import java.net.InetAddress

class OpenDNSExternalIPProvider extends ExternalIPProvider {

  val dnsClient = new SimpleDnsClient(InetAddress.getByName("resolver1.opendns.com"))

  def address(): Option[InetAddress] = dnsClient.address("myip", "opendns.com")
}
