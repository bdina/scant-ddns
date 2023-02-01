package protocol

import app.network.{Domain,Host}
import java.net.InetAddress

trait DDNSProvider {
  def update(host: Host, domain: Domain, address: InetAddress): Unit
}
