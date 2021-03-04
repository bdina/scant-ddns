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
