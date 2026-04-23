package protocol

import app.network.{Domain,Host}
import java.net.InetAddress

trait DNSProvider {
  def address(host: Host, domain: Domain): Option[InetAddress]
}
object DNSProvider {
  import scala.concurrent.Future
  def dns_lookup(host: Host, domain: Domain)(using dnsProvider: DNSProvider): Future[Option[InetAddress]] =
    Future { dnsProvider.address(host, domain) }(using concurrent.BlockingExecutionContext.executionContext)
}
