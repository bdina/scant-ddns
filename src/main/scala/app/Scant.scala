package app

object Scant extends App with ScantLogging with SystemManagement {
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

  def logMemoryStats(): Unit = {
    val stats = memoryStats
    logger.info(s"MEMORY - Used ${stats.used} MB :: Free ${stats.free} MB :: Total ${stats.total} MB :: Max ${stats.max} MB")
  }

  override def toString() = "Scant DDNS: a hardly sufficient Dynamic DNS updater"

  val (host, domain) = hostAndDomain()

  val daemon = if (args.length == 1) args(0) == "-d" else false

  import protocol._
  implicit val dnsProvider = SimpleDNSProvider()

  val ddnsProvider = NamecheapDDNSProvider()

  logger.info(s"Start $this ($availableProcessors cpu cores) - dns provider $dnsProvider :: ddns provider $ddnsProvider")

  import java.net.InetAddress
  import scala.concurrent.Future

  def execute: Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    (for {
      host_ip <- ExternalIPProvider.failover()
      dns_ip <- DNSProvider.dns_lookup(host, domain)
    } yield {
      (host_ip, dns_ip)
    }).map {
      case (Some(externalIp: InetAddress), Some(dnsIp: InetAddress)) if (dnsIp != externalIp) =>
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

  def cleanup: Future[Unit] = {
    import scala.concurrent.ExecutionContext.Implicits.global
    Future {
      logger.info("trigger GC")
      memoryCleanup()
      logMemoryStats()
    }
  }

  import scala.concurrent.Await
  import scala.concurrent.duration._
  if (!daemon) {
    Await.result(execute, 10.seconds)
  } else {
    val exec = concurrent.ScheduledExecutionContext(corePoolSize=2)

    val duration = 1.minutes
    val delay = 0.seconds

    exec.scheduleAtFixedRate(period=duration, initialDelay=delay) { cleanup }

    logger.info(s"running deamonized - scheduled task to execute on $duration duration after $delay delay")
    val cancelable = exec.scheduleAtFixedRate(period=duration, initialDelay=delay) { execute }

    Await.result(cancelable, Duration.Inf)
  }
}
