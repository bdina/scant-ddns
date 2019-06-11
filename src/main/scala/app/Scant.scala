package app

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

    val ipProvider   = new UPnPExternalIPProvider()
    val dnsProvider  = new SimpleDNSProvider()
    val ddnsProvider = new NamecheapDDNSProvider()

    val daemon = if ( args.length == 1 ) { "-d".equals(args(0)) } else { false }

    do {
      ipProvider.address match {
        case None => logger.severe("unable to fetch external ip from router!") ; System.exit(1)
        case Some(externalIp) => {
          val (host, domain) = hostAndDomain()

          logger.info(s"configured to update host '$host' for domain '$domain'")

          dnsProvider.address(host, domain) match {
            case None => logger.severe("unable to fetch host record from dns!") ; System.exit(1)
            case Some(dnsIp) => {
              val expected = externalIp
              val actual = dnsIp

              if (actual != expected) {
                logger.info(s"updating DNS with $expected")
                ddnsProvider.update(host,domain,externalIp)
              }
            }
          }
        }
      }
      if (daemon) { TimeUnit.MINUTES.sleep(1L) }
    } while (daemon)
  }
}
