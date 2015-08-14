import org.zeromq.{ ZMQ, ZMQException }, ZMQ.Context

class Server(context: Context, address: String) extends Helpers {
  import Server._

  val socket = context.socket(ZMQ.REP)

  try {
    socket.bind(address)
  } catch {
    case z: ZMQException =>
      throw new BindException("Unable to bind to " + address)
  }

  def serve(f: String => String) = {
    val req = socket.recv(0)
    val reqString = fromZMQBytes(req)
    socket.send(toZMQBytes(f(reqString)), 0)
  }

  def close(): Unit = {
    socket.unbind(address)
    socket.close()
  }
}

object Server {
  class BindException(message: String) extends RuntimeException(message)
}
