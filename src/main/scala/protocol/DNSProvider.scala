package protocol

import app.{Domain,Host}
import java.net.InetAddress

trait DNSProvider {
  def address(host: Host, domain: Domain): Option[InetAddress]
}
