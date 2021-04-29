package app

import datadog.trace.agent.test.base.HttpServerTest
import org.apache.struts.action.Action
import org.apache.struts.action.ActionForm
import org.apache.struts.action.ActionForward
import org.apache.struts.action.ActionMapping

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION

class ExceptionAction extends Action {

  @Override
  ActionForward execute(
    ActionMapping mapping,
    ActionForm form,
    HttpServletRequest request,
    HttpServletResponse response) {
    HttpServerTest.controller(EXCEPTION) {
      throw new Exception(EXCEPTION.body)
    }
  }
}
