package concurrent

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext

object BlockingExecutionContext extends app.ScantLogging {
  private val threadCounter = new AtomicInteger(1)
  private val threadFactory = new ThreadFactory {
    override def newThread(runnable: Runnable): Thread = {
      val threadName = s"scant-blocking-io-${threadCounter.getAndIncrement()}"
      val thread = new Thread(runnable, threadName)
      thread.setDaemon(true)
      thread
    }
  }

  private val service: ExecutorService = Executors.newCachedThreadPool(threadFactory)

  val executionContext: ExecutionContext = ExecutionContext.fromExecutorService(service)

  def shutdown(): Unit = {
    service.shutdown()
    logger.info("blocking execution context shutdown")
  }
}
