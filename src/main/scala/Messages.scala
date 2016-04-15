package org.nlogo.extensions.dnl

object Messages {
  sealed trait Request
  case class Reporter(reporter: String)    extends Request
  case class SyncCommand(command: String)  extends Request
  case class AsyncCommand(command: String) extends Request

  object Request {
    def fromString(s: String): Option[Request] = {
      val (requestType, request) = (s(0), s.drop(2))

      s.head match {
        case 'r' => Some(Reporter(request))
        case 'c' => Some(SyncCommand(request))
        case 'a' => Some(AsyncCommand(request))
        case _   => None
      }
    }

    def toString(r: Request): String = {
      r match {
        case Reporter(rep)      => "r:" + rep
        case SyncCommand(cmd)   => "c:" + cmd
        case AsyncCommand(cmd)  => "a:" + cmd
      }
    }
  }

  sealed trait Response
  case class CommandComplete(cmd: String)       extends Response
  case class InvalidMessage(message: String)    extends Response
  case class ExceptionResponse(message: String) extends Response
  case class LogoObject(dump: String)           extends Response

  object Response {
    def fromString(s: String): Option[Response] = {
      val (responseType, response) = (s(0), s.drop(2))

      responseType match {
        case 'l' => Some(LogoObject(response))
        case 'e' => Some(ExceptionResponse(response))
        case 'i' => Some(InvalidMessage(response))
        case 'x' => Some(CommandComplete(response))
        case _   => None
      }
    }

    def toString(r: Response): String = {
      r match {
        case LogoObject(d)        => "l:" + d
        case ExceptionResponse(e) => "e:" + e
        case InvalidMessage(i)    => "i:" + i
        case CommandComplete(c)   => "x:" + c
      }
    }
  }

}
