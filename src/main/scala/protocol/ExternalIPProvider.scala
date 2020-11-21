package protocol

import java.net.InetAddress

trait ExternalIPProvider {
  def address(): Option[InetAddress]
}
case object ExternalIPProvider extends app.ScantLogging {

  lazy val upnp = UPnPExternalIPProvider()
  lazy val opendns = OpenDNSExternalIPProvider()

  def apply(): ExternalIPProvider = {
    import app.Scant
    Scant.configuration().getProperty("ip.provider", "upnp") match {
      case "upnp" =>
        logger.info("CONFIGURED for UPnP external IP provider")
        upnp
      case "opendns" =>
        logger.info("CONFIGURED for OpenDNS external IP provider")
        opendns
      case _ =>
        logger.info("CONFIGURED (default) for UPnP external IP provider")
        upnp
    }
  }

  import scala.concurrent.Future
  def failover()(implicit ec: scala.concurrent.ExecutionContext): Future[Option[InetAddress]] = {
    Future {
      upnp.address() match {
        case res @ Some(_) => res
        case None =>
          logger.info("FAILOVER to use OpenDNS reverse resolution to find IP")
          secondary.address()
      }
    }
  }
}
