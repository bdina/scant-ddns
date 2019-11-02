package app

case class Host(val name: String) extends AnyVal
case class Domain(val name: String) extends AnyVal

trait ScantLogging {
  import java.util.logging.Logger
  val logger: Logger = Logger.getLogger(this.getClass.getName)
}
