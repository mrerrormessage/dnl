import Messages._
import Sockets.BindableSocket

object Server {
  type MaybeValidRequest = Either[String, Request]

  def stringToMaybeRequest(s: String) =
    Request.fromString(s).map(Right.apply).getOrElse(Left(s))

  def responseToString(r: Response): String =
    Response.toString(r)
}

import Server._

class Server(socket: BindableSocket[String, String])
  extends ComposableServer[MaybeValidRequest, Response](
    socket,
    stringToMaybeRequest,
    responseToString) {

  def serveResponse(p: Request => Response) =
    serveFunction { eitherStringOrReq =>
      try {
        eitherStringOrReq match {
          case Left(s)  => InvalidMessage(s)
          case Right(m) => p(m)
        }
      } catch {
        case e: Exception => ExceptionResponse(e.getMessage)
      }
    }
}

class ComposableServer[A, B](val socket: BindableSocket[String, String], recvToA: String => A, resultToReply: B => String) {
  def serve(f: String => String) =
    socket.send(f(socket.recv().getOrElse("")))

  def serveFunction(p: A => B) =
    serve(processResponse(_, p))

  def transform[C, D](atoc: A => C, dtob: D => B): ComposableServer[C, D] =
    new ComposableServer(socket, atoc compose recvToA, resultToReply compose dtob)

  private def processResponse(s: String, f: A => B): String =
    resultToReply(f(recvToA(s)))
}
