package protocol

import java.net.InetAddress

trait ExternalIPProvider {
  def address(): Option[InetAddress]
}
case object ExternalIPProvider {

  lazy val upnp = UPnPExternalIPProvider()
  lazy val opendns = OpenDNSExternalIPProvider()

  def apply(): ExternalIPProvider = {
    import app.Scant
    Scant.configuration().getProperty("ip.provider", "upnp") match {
      case "upnp" => upnp
      case "opendns" => opendns
      case _ => upnp
    }
  }

  import scala.concurrent.Future
  def failover()(implicit ec: scala.concurrent.ExecutionContext): Future[Option[InetAddress]] = {
    Future {
      upnp.address() match {
        case res @ Some(_) => res
        case None => opendns.address()
      }
    }
  }
}
