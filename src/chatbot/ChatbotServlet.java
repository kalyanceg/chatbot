package chatbot;

import java.io.IOException;
import javax.servlet.http.*;
/**Servlet never called.. Dont Look into this code **/
@SuppressWarnings("serial")
public class ChatbotServlet extends HttpServlet {
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setContentType("text/plain");
		resp.getWriter().println("Hello, world");
	}
}
