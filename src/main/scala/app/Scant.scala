package app

object Scant extends App with ScantLogging with SystemManagement {
  import java.util.Properties
  import app.network._

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

  val greeting = s"Scant DDNS $appVersion: a hardly sufficient Dynamic DNS updater"
  override def toString() = greeting

  val (host, domain) = hostAndDomain()

  val daemon = if (args.length == 1) args(0) == "-d" else false

  import protocol._
  implicit val dnsProvider = SimpleDNSProvider()

  val ddnsProvider = NamecheapDDNSProvider()
  val failoverProvider = OpenDNSExternalIPProvider() /* FIX seg fault */

  logger.info(s"Start $this ($availableProcessors cpu cores) - dns provider $dnsProvider :: ddns provider $ddnsProvider (runtime $runtimeVersion)")

  import java.net.InetAddress
  import scala.concurrent.Future
  import scala.async.Async.{async,await}

  def update()(implicit ec: scala.concurrent.ExecutionContext): Future[Unit] = {
    logMemoryStats()
    async {
      val host_ip = ExternalIPProvider.failover(failoverProvider)
      val dns_ip = DNSProvider.dns_lookup(host, domain)
      (await(host_ip), await(dns_ip))
    }.map {
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

  if (!daemon) {
    implicit val ec = scala.concurrent.ExecutionContext.global
    update()
  } else {
    val factory = concurrent.ScheduledExecutionContext.ScheduledThreadFactory()
    implicit val ec = concurrent.ScheduledExecutionContext(corePoolSize=1,threadFactory=factory)

    import scala.concurrent.duration._
    val duration = 1.minutes
    val delay = 0.seconds

    logger.info(s"running deamonized - scheduled task to execute on $duration duration after $delay delay")
    ec.scheduleAtFixedRate(period=duration, initialDelay=delay) { update() }
  }
}
