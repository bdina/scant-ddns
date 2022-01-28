package app

case class Host(val name: String) extends AnyVal
case class Domain(val name: String) extends AnyVal

trait ScantLogging {
  import java.util.logging.Logger

  val logger: Logger = Logger.getLogger(this.getClass.getName)
}

object System {
  final val ONE_MB = 1024*1024
  val runtime = Runtime.getRuntime
  val version = Runtime.version

  object Manifest {
    import java.util.jar.Manifest
    val manifest = try {
      new Manifest(System.getClass.getClassLoader.getResource("META-INF/MANIFEST.MF").openStream)
    } catch {
      case ex: Exception =>
        println(s"UNABLE TO READ MANIFEST -> $ex")
        new Manifest()
    }
    def attribute(name: String): Option[String] = Option(manifest.getMainAttributes.getValue(name))
  }

  case class Stats(used: Long, free: Long, total: Long, max: Long)

  def shutdownNow(code: Int = 0) = runtime.exit(code)
}

trait SystemManagement {
  import System._

  val runtimeVersion: String = s"${version}"
  val appVersion: String = Manifest.attribute("App-Version").getOrElse("beta-0.0.0")
  val availableProcessors: Int = runtime.availableProcessors

  def memoryCleanup(): Unit = runtime.gc()

  def memoryStats: Stats = {
    val used = (runtime.totalMemory - runtime.freeMemory) / ONE_MB
    val free = runtime.freeMemory / ONE_MB
    val total = runtime.totalMemory / ONE_MB
    val max = runtime.maxMemory / ONE_MB
    Stats(used=used,free=free,total=total,max=max)
  }
}
