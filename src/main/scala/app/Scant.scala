package app

object Scant extends ScantLogging with SystemManagement {
  import java.util.Properties
  import app.network._
  import scala.util.Using

  private lazy val loadedConfiguration: Properties = {
    import java.nio.file.{Files,Paths,StandardOpenOption}
    val properties = new Properties()
    Using.resource(Files.newInputStream(Paths.get("ddns.properties"), StandardOpenOption.READ)) { fin =>
      properties.load(fin)
    }
    properties
  }
  def configuration(): Properties = loadedConfiguration

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

  val greeting = s"Scant DDNS $appVersion: a hardly sufficient Dynamic DNS updater"
  override def toString() = greeting

  import protocol._
  private val (host, domain) = hostAndDomain()
  private implicit val dnsProvider: DNSProvider = SimpleDNSProvider()
  private val ddnsProvider = NamecheapDDNSProvider()

  import java.net.InetAddress
  import scala.concurrent.Future

  def update()(implicit ec: scala.concurrent.ExecutionContext): Future[Unit] = {
    logMemoryStats()
    val hostIp = ExternalIPProvider.failover()
    val dnsIp = DNSProvider.dns_lookup(host, domain)
    hostIp.zip(dnsIp).map {
      case (Some(externalAddress: InetAddress), Some(dnsAddress: InetAddress)) if (dnsAddress != externalAddress) =>
        logger.info(s"updating DDNS via $ddnsProvider")
        ddnsProvider.update(host, domain, externalAddress)
      case (Some(externalAddress: InetAddress), Some(dnsAddress: InetAddress)) =>
        logger.info(s"nothing to update")
      case (None, Some(_)) =>
        logger.severe("unable to fetch external IP!")
      case (Some(_), None) =>
        logger.severe("unable to fetch host record")
      case (None, None) =>
        logger.severe("unable to fetch external IP and host record")
    }.recover {
      case exception => logger.severe(s"unable to process: ${exception.getMessage}")
    }
  }

  def main(args: Array[String]): Unit = {
    val daemon = args.length == 1 && args(0) == "-d"

    logger.info(s"Start $this ($availableProcessors cpu cores) - dns provider $dnsProvider :: ddns provider $ddnsProvider (runtime $runtimeVersion)")

    if (!daemon) {
      implicit val ec = scala.concurrent.ExecutionContext.global
      import scala.concurrent.Await
      import scala.concurrent.duration._
      try {
        Await.result(update(), 15.seconds)
      } finally {
        concurrent.BlockingExecutionContext.shutdown()
      }
    } else {
      val factory = concurrent.ScheduledExecutionContext.ScheduledThreadFactory()
      implicit val ec = concurrent.ScheduledExecutionContext(corePoolSize=1,threadFactory=factory)

      import scala.concurrent.duration._
      val duration = 1.minutes
      val delay = 0.seconds

      logger.info(s"running deamonized - scheduled task to execute on $duration duration after $delay delay")
      ec.scheduleAtFixedRate(period=duration, initialDelay=delay) { update() }
      Runtime.getRuntime.addShutdownHook(new Thread(() => {
        ec.shutdown(timeout=5)
        concurrent.BlockingExecutionContext.shutdown()
      }))
    }
  }
}
