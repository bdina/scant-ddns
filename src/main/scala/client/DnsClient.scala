package client

import java.net.InetAddress

trait DnsClient {
  def query(host: String, domain: String): Option[InetAddress]
  def address(host: String, domain: String): Option[InetAddress]
}
