import Messages._

import org.nlogo.agent.Observer
import org.nlogo.api.{ Dump, SimpleJobOwner }
import org.nlogo.app.App

import org.zeromq.ZMQ, ZMQ.Context

class ServerThread(context: Context, address: String) extends Thread {
  @volatile var stopping: Boolean = false
  def close(): Unit = {
    stopping = true
  }

  override def run(): Unit = {
    val server = new Server(context, address)
    while (!stopping) {
      server.serveResponse {
        case Reporter(rep) =>
          val workspace = App.app.workspace
          val jobOwner = new SimpleJobOwner("DNL", workspace.world.mainRNG, classOf[Observer])
          val compiledReporter = workspace.compileReporter(rep)
          val reporterResult = workspace.runCompiledReporter(jobOwner, compiledReporter)
          LogoObject(Dump.logoObject(reporterResult, true, true))
        case Command(com) =>
          LogoObject(Dump.logoObject("", true, true))
      }
    }
    server.close()
  }
}
