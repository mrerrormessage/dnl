object Messages {
  sealed trait Request
  case class Reporter(reporter: String) extends Request
  case class Command(command: String) extends Request

  sealed trait Response
  case class InvalidMessage(message: String) extends Response
  case class ExceptionResponse(message: String) extends Response
  case class LogoObject(dump: String) extends Response
}
