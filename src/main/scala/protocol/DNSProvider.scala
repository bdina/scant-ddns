package protocol

import java.net.InetAddress

trait DNSProvider {
  def address(host: String, domain: String): Option[InetAddress]
}
