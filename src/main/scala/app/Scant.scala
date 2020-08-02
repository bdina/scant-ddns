package app

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

  override def toString() = "Scant DDNS: a hardly sufficient Dynamic DNS updater"

  val (host, domain) = hostAndDomain()

  val daemon = if (args.length == 1) { "-d".equals(args(0)) } else { false }

  import protocol._
  implicit val dnsProvider = SimpleDNSProvider()

  val ddnsProvider = NamecheapDDNSProvider()

  logger.info(s"Start $this - dns provider $dnsProvider :: ddns provider $ddnsProvider")

  import java.net.InetAddress
  import scala.concurrent.Future

  def execute: Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val fetch = for {
      host_ip <- ExternalIPProvider.failover()
      dns_ip <- DNSProvider.dns_lookup(host, domain)
    } yield (host_ip, dns_ip)

    fetch.map {
      case (Some(externalIp: InetAddress), Some(dnsIp: InetAddress)) if (!externalIp.equals(dnsIp)) =>
        logger.info(s"externalIp:$externalIp :: dnsIp:$dnsIp - updating DNS via $ddnsProvider")
        ddnsProvider.update(host, domain, externalIp)
      case (Some(externalIp: InetAddress), Some(dnsIp: InetAddress)) =>
        logger.info(s"externalIp:$externalIp :: dnsIp:$dnsIp - nothing to update")
      case (None, Some(_)) => logger.severe("unable to fetch external IP!")
      case (Some(_), None) => logger.severe("unable to fetch host record")
      case (None, None) => logger.severe("unable to fetch external IP and host record")
    }.recover {
      case exception => logger.severe(s"unable to process: ${exception.getMessage}")
    }
  }

  import scala.concurrent.Await
  import scala.concurrent.duration._
  if (!daemon) {
    Await.result(execute, 10.seconds)
  } else {
    val exec = concurrent.ScheduledExecutionContext(corePoolSize=1)
    val duration = 1.minutes
    val delay = 0.seconds
    val cancelable = exec.scheduleAtFixedRate(period=duration, initialDelay=delay) { execute }
    logger.info(s"running deamonized - scheduled task to execute on $duration duration after $delay delay")
    Await.result(cancelable, Duration.Inf)
  }
}
