package protocol

import client.SimpleDnsClient
import java.net.InetAddress

import app.Scant

object SimpleDNSProvider {

  val DefaultServerAddress: String = "8.8.8.8"

  def dnsServer(): InetAddress =
    InetAddress.getByName(Scant.configuration().getProperty("dns.server.ip", DefaultServerAddress))
}

class SimpleDNSProvider(val resolver: InetAddress = SimpleDNSProvider.dnsServer()) extends DNSProvider {

  val dnsClient = new SimpleDnsClient(resolver)

  override def address(host: String, domain: String): Option[InetAddress] = dnsClient.address(host,domain)
}
