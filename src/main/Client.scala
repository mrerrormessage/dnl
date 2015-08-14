import java.util.concurrent.TimeoutException

import org.zeromq.ZMQ, ZMQ.Context

class Client(context: Context) extends Helpers {
  def fetchResponse(address: String, reporter: String): String = {
    val reqSocket = context.socket(ZMQ.REQ)
    reqSocket.connect(address)
    reqSocket.setSendTimeOut(500)
    val sent = reqSocket.send(toZMQBytes(reporter))
    if (sent) {
      reqSocket.setReceiveTimeOut(500)
      Option(reqSocket.recv()).map(fromZMQBytes)
        .getOrElse(throw new TimeoutException("no response received in time"))
    }
    else {
      throw new TimeoutException("unable to establish connection")
    }
  }
}
