package concurrent

import java.util.concurrent.ThreadPoolExecutor.AbortPolicy
import java.util.concurrent._

import scala.concurrent.{Promise,Future}

case class ScheduledExecutionContext(
    corePoolSize: Int = Runtime.getRuntime.availableProcessors
  , threadFactory: ThreadFactory = ScheduledExecutionContext.defaultThreadFactory
  , rejectedHandler: RejectedExecutionHandler = ScheduledExecutionContext.defaultRejectedHandler
  ) extends scala.concurrent.ExecutionContext with app.ScantLogging {
  import scala.concurrent.duration.Duration
  import scala.util.Try

  private val execSrvc: ScheduledExecutorService = new ScheduledThreadPoolExecutor(corePoolSize, threadFactory, rejectedHandler)

  def execute(runnable: Runnable): Unit = execSrvc.schedule(runnable, Duration.Zero.length, Duration.Zero.unit)

  def reportFailure(cause: Throwable): Unit = logger.severe(s"failure ${cause.getMessage}")

  def shutdown(timeout: Long = 0, unit: TimeUnit = TimeUnit.SECONDS): Unit = {
    execSrvc.awaitTermination(timeout, unit)
    val cancelled = execSrvc.shutdownNow()
    logger.info(s"$execSrvc shutdown NOW - cancelled ${cancelled.size} tasks")
  }

  def schedule[T](by: Duration)(operation: => T): CancellableFuture[T] = {
    val promise = Promise[T]()
    val scheduledFuture: ScheduledFuture[_] = execSrvc.schedule(new Runnable {
      override def run(): Unit = promise.complete(Try(operation))
    }, by.length, by.unit)
    DelegatingCancellableFuture(promise.future, scheduledFuture.cancel)
  }
  def scheduleAtFixedRate[T](period: Duration, initialDelay: Duration)(operation: => T): CancellableFuture[T] = {
    val promise = Promise[T]()
    val scheduledFuture: ScheduledFuture[_] = execSrvc.scheduleAtFixedRate(new Runnable {
      override def run(): Unit = operation
    }, initialDelay.toMillis, period.length, period.unit)
    DelegatingCancellableFuture(promise.future, scheduledFuture.cancel)
  }
  def scheduleWithFixedDelay[T](delay: Duration, initialDelay: Duration)(operation: => T): CancellableFuture[T] = {
    val promise = Promise[T]()
    val scheduledFuture: ScheduledFuture[_] = execSrvc.scheduleWithFixedDelay(new Runnable {
      override def run(): Unit = operation
    }, initialDelay.toMillis, delay.length, delay.unit)
    DelegatingCancellableFuture(promise.future, scheduledFuture.cancel)
  }
}
object ScheduledExecutionContext {
  private val defaultRejectedHandler: RejectedExecutionHandler = new AbortPolicy()
  private val defaultThreadFactory: ThreadFactory = ScheduledThreadFactory()

  import java.util.concurrent.atomic.AtomicInteger
  case class ScheduledThreadFactory(daemonize: Boolean = false) extends ThreadFactory with app.ScantLogging {
    import ScheduledThreadFactory._
    private val threadNumber = new AtomicInteger(1)

    private val s = System.getSecurityManager()

    private val group = if (s != null) s.getThreadGroup() else Thread.currentThread().getThreadGroup()
    private val namePrefix = s"scala-execution-context-scheduled-${poolNumber.getAndIncrement()}-thread-"

    override def newThread(r: Runnable): Thread = {
      val t = new Thread(group, r, s"$namePrefix${threadNumber.getAndIncrement()}", 0)
      t.setDaemon(daemonize)
      if (t.getPriority() != Thread.NORM_PRIORITY) t.setPriority(Thread.NORM_PRIORITY)
      logger.info(s"created $t")
      t
    }
  }
  object ScheduledThreadFactory {
    private val poolNumber = new AtomicInteger(1)
  }

  final lazy val global = ScheduledExecutionContext(corePoolSize=1)

  object Implicits {
    implicit final def global: scala.concurrent.ExecutionContext = ScheduledExecutionContext.global
  }
}

trait CancellableFuture[T] {
  def future: Future[T]
  def cancel(mayInterruptIfRunning: Boolean): Boolean
}
object CancellableFuture {
  import scala.language.implicitConversions
  implicit def extractFuture[T](cf: CancellableFuture[T]): Future[T] = cf.future
}

private case class DelegatingCancellableFuture[T](val future: Future[T], cancelMethod: (Boolean) => Boolean) extends CancellableFuture[T] {
  def cancel(interruptIfRunning: Boolean): Boolean = cancelMethod(interruptIfRunning)
}
