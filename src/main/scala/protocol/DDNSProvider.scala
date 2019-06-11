package protocol

import java.net.InetAddress

trait DDNSProvider {
  def update(host: String, domain: String, address: InetAddress): Unit
}
