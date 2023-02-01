package client

import app.network.{Domain,Host}
import java.net.InetAddress

trait DnsClient {
  def query(host: Host, domain: Domain): Option[InetAddress]
}
