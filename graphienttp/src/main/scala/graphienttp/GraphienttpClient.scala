package graphienttp
import cats.effect.Sync
import com.softwaremill.sttp.{sttp, BodySerializer, Id, Request, Response, StringBody, SttpBackend, Uri}
import graphient._
import io.circe.Encoder
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import sangria.renderer.QueryRenderer
import sangria.schema.Schema

case class QueryRequest[T](query: String, variables: T)
object QueryRequest {
  implicit val config = Configuration.default

  implicit def queryRequestEncoder[T: Encoder] = deriveEncoder[QueryRequest[T]]

  implicit def queryRequestSerializer[T: Encoder]: BodySerializer[QueryRequest[T]] = { tokenRequest =>
    val serialized = tokenRequest.asJson.noSpaces

    StringBody(serialized, "UTF-8", Some("application/json"))
  }
}

class GraphienttpClient[F[_]](
    schema:         Schema[_, _],
    endpoint:       Uri
)(implicit backend: SttpBackend[F, Nothing], effect: Sync[F]) {

  val queryGenerator = new QueryGenerator(schema)

  def runQuery[P: Encoder](query: Query[_, _], variables: P): F[Response[String]] = {
    queryGenerator.generateQuery(query) match {
      case Left(error) => effect.raiseError(error)
      case Right(q) =>
        val qJson   = QueryRenderer.render(q)
        val payload = QueryRequest(qJson, variables)
        sttp
          .body(payload)
          .post(endpoint)
          .send()
    }
  }

  def runMutation() = {}

}
