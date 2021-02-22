package protocol

import java.net.http.HttpClient

object Http {
  implicit lazy val httpClient: HttpClient = HttpClient.newBuilder().build()
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

    def tryPost(uri: URI, body: String, headers: List[(String,String)]): Try[String] = {
      val _headers = headers.flatMap { case (a,b) => a :: List(b) }
      val _body = HttpRequest.BodyPublishers.ofString(body)
      val request = HttpRequest.newBuilder(uri).headers(_headers:_*).POST(_body).build()
      Try { hc.send(request, HttpResponse.BodyHandlers.ofString()).body }
    }
  }
}