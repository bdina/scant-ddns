package app

import java.net.InetAddress

import protocol._
import java.util.Properties
import java.util.logging.Logger

object Scant {

  val logger: Logger = Logger.getLogger(this.getClass.getName)

  def configuration(): Properties = {
    import java.nio.file.{Files,Paths,StandardOpenOption}
    val fin = Files.newInputStream(Paths.get("ddns.properties"), StandardOpenOption.READ)
    val properties = new Properties()
    properties.load(fin)
    properties
  }

  def hostAndDomain(): (String, String) = {
    val config = configuration()
    val host = config.getProperty("ddns.host" )
    val domain = config.getProperty("ddns.domain")
    (host, domain)
  }

  def main(args: Array[String]): Unit = {

    import java.util.concurrent.TimeUnit
    import scala.concurrent.Await
    import scala.concurrent.ExecutionContext.Implicits.global
    import scala.concurrent.Future
    import scala.concurrent.duration._
    import scala.language.postfixOps
    import scala.util.{Failure, Success}

    val ipProvider   = IpProviderFactory.create()
    val dnsProvider  = new SimpleDNSProvider()
    val ddnsProvider = new NamecheapDDNSProvider()

    def ip_lookup = Future { ipProvider.address() }
    def dns_lookup (host: String, domain: String) = Future { dnsProvider.address(host, domain) }

    val (host, domain) = hostAndDomain()

    val daemon = if ( args.length == 1 ) { "-d".equals(args(0)) } else { false }

    do {
      val externalIp = ip_lookup
      val dnsIp      = dns_lookup(host, domain)

      val result = for {
        host_ip <- externalIp
        dns_ip <- dnsIp
      } yield (host_ip, dns_ip)

      if (!daemon) {
        Await.result(externalIp, 10 second)
        Await.result(dnsIp, 10 second)
      } else {
        TimeUnit.MINUTES.sleep(1L)
      }

      result onComplete {
        case Success(value) =>
          value match {
            case (Some(externalIp: InetAddress), Some(dnsIp: InetAddress)) =>
              if (!externalIp.equals(dnsIp)) {
                logger.info(s"updating DNS with $externalIp")
                ddnsProvider.update(host, domain, externalIp)
              }
            case (None, Some(_)) => logger.severe("unable to fetch external IP!")
            case (Some(_), None) => logger.severe("unable to fetch host record")
            case (None, None) => logger.severe("unable to fetch external IP and host record")
          }
        case Failure(exception) => logger.severe(s"unable to process: ${exception.getMessage}")
      }
    } while (daemon)
  }
}

object IpProviderFactory {
  def create(): ExternalIPProvider = {
    Scant.configuration().getProperty("ip.provider", "upnp") match {
      case "upnp" => new UPnPExternalIPProvider()
      case "opendns" => new OpenDNSExternalIPProvider()
      case _ => new UPnPExternalIPProvider()
    }
  }
}
