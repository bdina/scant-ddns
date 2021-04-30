package client

object Constants {
  val SO_TIMEOUT: Int = java.util.concurrent.TimeUnit.SECONDS.toMillis(3L).toInt
}

package object net {
  import java.net.{DatagramPacket,DatagramSocket}

  import scala.util.Try

  implicit class EnhancedDatagramSocket(ds: DatagramSocket) extends app.ScantLogging {

    logger.fine(s"Set SoTimeout on DatagramSocket ${Constants.SO_TIMEOUT} ms")
    ds.setSoTimeout(Constants.SO_TIMEOUT)

    def trySend(packet: DatagramPacket): Try[Unit] = Try { ds.send(packet) }
    def tryReceive(): Try[Array[Byte]] = Try {
      val buf = new Array[Byte](1024)
      val packet = new DatagramPacket(buf, buf.length)
      ds.receive(packet)
      logger.finer(s"\n\nReceived: ${packet.getLength} bytes")
      buf
    }
  }
}
