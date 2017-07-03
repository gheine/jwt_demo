package jwt_demo

import scala.concurrent.duration._
import scalaz._
import Scalaz._
import scalaz.concurrent.Task

import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.server.{AuthMiddleware, ServerApp}
import org.http4s.server.blaze.BlazeBuilder
import pdi.jwt.{Jwt, JwtAlgorithm}

case class User(name: String, password: String)

case class Token(token: String)

case class Key(user: String, exp: Long = (System.currentTimeMillis() + Config.TokenExpiryMs)) {
  def expired: Boolean = exp < System.currentTimeMillis()
}

object Config {
  val SecretKey = "secretKey"
  val TokenExpiryMs = 1.minutes.toMillis
}

object Service extends ServerApp {
  implicit def circeJsonDecoder[A](implicit decoder: Decoder[A]) = org.http4s.circe.jsonOf[A]
  implicit def circeJsonEncoder[A](implicit encoder: Encoder[A]) = org.http4s.circe.jsonEncoderOf[A]

  val AuthScheme = "Bearer"
  val UnAuthed = Unauthorized(Challenge(AuthScheme, "jwt_demo"))
  val users = new collection.mutable.HashMap[String, String]()

  val routes = HttpService {
    case GET -> Root / "_ping_" => Ok("pong")
    case HEAD -> Root / "_ping_" => Ok()

    case req @ POST -> Root / "register" => req.attemptAs[User].run.flatMap {
      case \/-(user: User) if (!users.contains(user.name)) => {
        users.put(user.name, user.password)
        val token = Jwt.encode(Key(user.name).asJson.noSpaces, Config.SecretKey, JwtAlgorithm.HS256)
        Ok(Token(token))
      }
      case \/-(_) => UnAuthed
      case _ => BadRequest()
    }

    case req @ POST -> Root / "login" => req.attemptAs[User].run.flatMap {
      case \/-(user: User) if (users.get(user.name).exists(_ == user.password)) => {
        val token = Jwt.encode(Key(user.name).asJson.noSpaces, Config.SecretKey, JwtAlgorithm.HS256)
        Ok(Token(token))
      }
      case \/-(_) => UnAuthed
      case _ => BadRequest()
    }
  }

  val authUser: Service[Request, String \/ Key] = Kleisli { req =>
    Task.delay(for {
      headerVal <- req.headers.get(Authorization).map(_.value).toRightDisjunction("Missing Authorization header").ensure("Invalid Authorization header")(_.startsWith(s"$AuthScheme "))
      token = headerVal.drop(AuthScheme.length + 1) // Remove auth scheme prefix
      claim <- Jwt.decodeRaw(token, Config.SecretKey, Seq(JwtAlgorithm.HS256)).toDisjunction.leftMap(_ => "Invalid jwt token")
      json <- parse(claim).disjunction.leftMap(_ => "Invalid jwt token")
      key <- json.as[Key].disjunction.leftMap(_ => "Invalid jwt token").ensure("Token expired")(!_.expired)
    } yield key)
  }

  val onFailure: AuthedService[String] = Kleisli(req => UnAuthed.withBody(req.authInfo))

  val authedRoutes: AuthedService[Key] = AuthedService {
    case GET -> Root / "hello" as key => Ok(s"Hello ${key.user}!")
  }

  override def server(args: List[String]) = {
    BlazeBuilder
      .bindHttp(8080, "0.0.0.0")
      .mountService(AuthMiddleware(authUser, onFailure)(authedRoutes))
      .mountService(routes)
      .start
  }
}

