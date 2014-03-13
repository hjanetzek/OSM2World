package org.osm2world.tileserver;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.osm2world.console.TileGenerator;

public class TileServer {
	public static void main(String[] args) throws Exception {
		Server server = new Server(8080);
		server.setHandler(new TileHandler());
		server.start();
		server.join();
	}

	public static class TileHandler extends AbstractHandler {
		public void handle(String target, Request baseRequest,
				HttpServletRequest request,
				HttpServletResponse response)
				throws IOException, ServletException {

			String xx = request.getParameter("x");
			String yy = request.getParameter("y");
			String zz = request.getParameter("z");
			int x = Integer.parseInt(xx);
			int y = Integer.parseInt(yy);
			int z = Integer.parseInt(zz);
			
			TileGenerator.createTile(x, y, z);
			
			response.setContentType("text/html;charset=utf-8");
			response.setStatus(HttpServletResponse.SC_OK);
			baseRequest.setHandled(true);
			response.getWriter().println("<h1>Hello World " + x + "/" + y + "/" + z + "</h1>");
		}
	}

}
