import org.zeromq.{ ZMQ, ZMQException }, ZMQ.Context
import Messages._

class Server(context: Context, val address: String) extends Helpers {
  import Server._

  val socket = context.socket(ZMQ.REP)

  try {
    socket.bind(address)
    socket.setLinger(0)
  } catch {
    case z: ZMQException =>
      close()
      throw new BindException("Unable to bind to " + address)
  }

  def serve(f: String => String) = {
    val req = socket.recv(0)
    val reqString = fromZMQBytes(req)
    socket.send(toZMQBytes(f(reqString)), 0)
  }

  def serveResponse(p: PartialFunction[Request, Response]) = {
    val req = socket.recv(0)
    val reqString = fromZMQBytes(req)
    val request = reqString.head match {
      case 'r' => Some(Reporter(reqString.drop(2)))
      case 'c' => Some(Command(reqString.drop(2)))
      case 'a' => Some(AsyncCommand(reqString.drop(2)))
      case _   => None
    }
    val response =
      try {
        request.map(p).getOrElse(InvalidMessage(reqString))
      } catch {
        case e: Exception => ExceptionResponse(e.getMessage)
      }
    socket.send(toZMQBytes(translateResponse(response)), 0)
  }

  def translateResponse(r: Response): String = {
    r match {
      case LogoObject(d)        => "l:" + d
      case ExceptionResponse(e) => "e:" + e
      case InvalidMessage(i)    => "i:" + i
      case CommandComplete(c)   => "x:" + c
    }
  }

  def close(): Unit = {
    socket.unbind(address)
    socket.close()
  }
}

object Server {
  class BindException(message: String) extends RuntimeException(message)
}
