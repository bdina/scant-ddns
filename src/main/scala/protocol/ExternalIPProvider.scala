package protocol

import java.net.InetAddress

trait ExternalIPProvider {
  def address(): Option[InetAddress]
}
case object ExternalIPProvider {
  def apply(): ExternalIPProvider = {
    import app.Scant
    Scant.configuration().getProperty("ip.provider", "upnp") match {
      case "upnp" => UPnPExternalIPProvider()
      case "opendns" =>OpenDNSExternalIPProvider()
      case _ => UPnPExternalIPProvider()
    }
  }
}
