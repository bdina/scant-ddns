package app

case class Host(val name: String) extends AnyVal
case class Domain(val name: String) extends AnyVal

trait ScantLogging {
  import java.util.logging.Logger

  val logger: Logger = Logger.getLogger(this.getClass.getName)
}

object System {
  val ONE_MB = 1024*1024
  val runtime = Runtime.getRuntime

  case class Stats(used: Long, free: Long, total: Long, max: Long)
}

trait SystemManagement {
  import System._

  def availableProcessors: Int = runtime.availableProcessors

  def memoryCleanup(): Unit = runtime.gc()

  def memoryStats: Stats = {
    val used = (runtime.totalMemory - runtime.freeMemory) / ONE_MB
    val free = runtime.freeMemory / ONE_MB
    val total = runtime.totalMemory / ONE_MB
    val max = runtime.maxMemory / ONE_MB
    Stats(used=used,free=free,total=total,max=max)
  }
}
