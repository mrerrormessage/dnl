import java.util.concurrent.TimeoutException

import org.zeromq.ZMQ, ZMQ.Context

import Messages._

class Client(context: Context) extends Helpers {
  def rawRequest(address: String, reporter: String): String = {
    val reqSocket = context.socket(ZMQ.REQ)
    reqSocket.setLinger(0)
    try {
      reqSocket.connect(address)
      reqSocket.setSendTimeOut(3000)
      val sent = reqSocket.send(toZMQBytes(reporter))
      if (sent) {
        reqSocket.setReceiveTimeOut(1000)
        Option(reqSocket.recv()).map(fromZMQBytes)
          .getOrElse(throw new TimeoutException("response timeout"))
      }
      else {
        throw new TimeoutException("unable to connect")
      }
    } finally {
      reqSocket.close()
    }
  }

  def request(address: String, request: Request): Response = {
    val reqString = request match {
      case Reporter(rep) => "r:" + rep
      case Command(cmd)  => "c:" + cmd
    }
    translateResponse(rawRequest(address, reqString))
  }

  def translateResponse(r: String): Response = {
    val (responseType, response) = (r(0), r.drop(2))

    responseType match {
      case 'l' => LogoObject(response)
      case 'e' => ExceptionResponse(response)
      case 'i' => InvalidMessage(response)
      case 'x' => CommandComplete(response)
    }
  }
}
