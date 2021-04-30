package protocol

import java.net.http.HttpClient

object Http {
  import java.time.Duration
  import java.util.concurrent.Executors
  implicit lazy val httpClient: HttpClient = HttpClient.newBuilder()
                                                       .followRedirects(HttpClient.Redirect.NORMAL)
                                                       .connectTimeout(Duration.ofSeconds(10))
                                                       .executor(Executors.newFixedThreadPool(1))
                                                       .build()
}

package object http {
  import java.net.URI
  import java.net.http.{HttpRequest,HttpResponse}

  import scala.util.Try

  implicit class EnhancedHttpClient(hc: HttpClient) {
    def tryGet(uri: URI): Try[String] = {
      val request = HttpRequest.newBuilder(uri).GET.build()
      Try { hc.send(request, HttpResponse.BodyHandlers.ofString()).body }
    }

    def tryPost(uri: URI, body: String, headers: Map[String,String]): Try[String] = {
      val _headers = headers.flatMap { case (k, v) => List(k, v) }.toSeq
      val _body = HttpRequest.BodyPublishers.ofString(body)
      val request = HttpRequest.newBuilder(uri).headers(_headers:_*).POST(_body).build()
      Try { hc.send(request, HttpResponse.BodyHandlers.ofString()).body }
    }
  }
}

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
    def tryReceive(bytes: Int = 1024): Try[DatagramPacket] = Try {
      val buf = new Array[Byte](bytes)
      val packet = new DatagramPacket(buf, buf.length)
      ds.receive(packet)
      logger.finer(s"\n\nReceived: ${packet.getLength} bytes")
      packet
    }
  }
}
