package protocol

import java.net.InetAddress

trait ExternalIPProvider {
  def address(): Option[InetAddress]
}
