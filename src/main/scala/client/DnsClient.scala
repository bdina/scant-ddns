package client

import app.{Domain,Host}
import java.net.InetAddress

trait DnsClient {
  def query(host: Host, domain: Domain): Option[InetAddress]
  def address(host: Host, domain: Domain): Option[InetAddress]
}
