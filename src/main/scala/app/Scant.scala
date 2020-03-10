package app

import scala.concurrent.ExecutionContext.Implicits.global

object Scant extends App with ScantLogging {
  import java.util.Properties

  def configuration(): Properties = {
    import java.nio.file.{Files,Paths,StandardOpenOption}
    val fin = Files.newInputStream(Paths.get("ddns.properties"), StandardOpenOption.READ)
    val properties = new Properties()
    properties.load(fin)
    properties
  }

  def hostAndDomain(): (Host, Domain) = {
    val config = configuration()
    val host = config.getProperty("ddns.host" )
    val domain = config.getProperty("ddns.domain")
    (Host(host), Domain(domain))
  }

  val (host, domain) = hostAndDomain()

  val daemon = if (args.length == 1) { "-d".equals(args(0)) } else { false }

  import protocol._
  implicit val dnsProvider = SimpleDNSProvider()

  val ddnsProvider = NamecheapDDNSProvider()

  do {
    import java.net.InetAddress
    import java.util.concurrent.TimeUnit

    import scala.concurrent.Await
    import scala.concurrent.duration._
    import scala.util.{Failure, Success}

    val result = for {
      host_ip <- ExternalIPProvider.failover
      dns_ip <- DNSProvider.dns_lookup(host, domain)
    } yield (host_ip, dns_ip)

    if (!daemon) {
      import scala.language.postfixOps
      Await.result(result, 10 second)
    } else {
      TimeUnit.MINUTES.sleep(1L)
    }

    result onComplete {
      case Success(value) =>
        value match {
          case (Some(externalIp: InetAddress), Some(dnsIp: InetAddress)) =>
            if (!externalIp.equals(dnsIp)) {
              logger.info(s"updating DNS with $externalIp via $ddnsProvider")
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
