package jwt_demo

import java.time.{Duration, Instant}
import scalaz._
import Scalaz._
import scalaz.concurrent.Task

import io.circe._
import io.circe.generic.auto._
import org.http4s._
import org.http4s.dsl._
import org.http4s.headers.Authorization
import org.http4s.server.{AuthMiddleware, ServerApp}
import org.http4s.server.blaze.BlazeBuilder
import pdi.jwt.{JwtCirce, JwtAlgorithm, JwtClaim}

case class User(name: String, password: String)

case class Token(token: String)

case class Login(user: String)

object Config {
  val SecretKey = "secretKey"
  val TokenExpiry = Duration.ofMinutes(1)
}

object Service extends ServerApp {
  val AuthScheme = "Bearer"
  val UnAuthed = Unauthorized(Challenge(AuthScheme, "jwt_demo"))

  val users = new collection.mutable.HashMap[String, String]()

  implicit def circeJsonDecoder[A](implicit decoder: Decoder[A]) = org.http4s.circe.jsonOf[A]
  implicit def circeJsonEncoder[A](implicit encoder: Encoder[A]) = org.http4s.circe.jsonEncoderOf[A]

  private def newClaim(user: String) = {
    val now = Instant.now
    JwtClaim(expiration = Some(now.plus(Config.TokenExpiry).getEpochSecond),
             issuedAt = Some(now.getEpochSecond),
             subject = Some(user))
  }

  val routes = HttpService {
    case GET -> Root / "_ping_" => Ok("pong")
    case HEAD -> Root / "_ping_" => Ok()

    case req @ POST -> Root / "register" => req.attemptAs[User].run.flatMap {
      case \/-(user: User) if (!users.contains(user.name)) => {
        users.put(user.name, user.password)
        val token = JwtCirce.encode(newClaim(user.name), Config.SecretKey, JwtAlgorithm.HS256)
        Ok(Token(token))
      }
      case \/-(_) => UnAuthed
      case _ => BadRequest()
    }

    case req @ POST -> Root / "login" => req.attemptAs[User].run.flatMap {
      case \/-(user: User) if (users.get(user.name).exists(_ == user.password)) => {
        val token = JwtCirce.encode(newClaim(user.name), Config.SecretKey, JwtAlgorithm.HS256)
        Ok(Token(token))
      }
      case \/-(_) => UnAuthed
      case _ => BadRequest()
    }
  }

  val authUser: Service[Request, String \/ Login] = Kleisli { req =>
    Task.delay(for {
      headerVal <- req.headers.get(Authorization).map(_.value).toRightDisjunction("Missing Authorization header").ensure("Invalid Authorization header")(_.startsWith(s"$AuthScheme "))
      token = headerVal.drop(AuthScheme.length + 1) // Remove auth scheme prefix
      claim <- JwtCirce.decode(token, Config.SecretKey, Seq(JwtAlgorithm.HS256)).toDisjunction.leftMap(_ => "Invalid jwt token").ensure("Invalid jwt token")(_.isValid)
      login <- claim.subject.map(Login(_)).toRightDisjunction("Invalid jwt token")
    } yield login)
  }

  val onFailure: AuthedService[String] = Kleisli(req => UnAuthed.withBody(req.authInfo))

  val authedRoutes: AuthedService[Login] = AuthedService {
    case GET -> Root / "hello" as login => Ok(s"Hello ${login.user}!")
  }

  override def server(args: List[String]) = {
    BlazeBuilder
      .bindHttp(8080, "0.0.0.0")
      .mountService(AuthMiddleware(authUser, onFailure)(authedRoutes))
      .mountService(routes)
      .start
  }
}

