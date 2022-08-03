package com.x.server.console.server.web;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TimeZone;
import java.util.stream.Stream;

import javax.servlet.DispatcherType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.AsyncRequestLogWriter;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import com.alibaba.druid.support.http.StatViewServlet;
import com.alibaba.druid.support.http.WebStatFilter;
import com.x.base.core.project.x_program_center;
import com.x.base.core.project.config.Config;
import com.x.base.core.project.config.WebServer;
import com.x.base.core.project.config.WebServers;
import com.x.base.core.project.logger.Logger;
import com.x.base.core.project.logger.LoggerFactory;
import com.x.base.core.project.tools.DefaultCharset;
import com.x.server.console.server.JettySeverTools;
import com.x.server.console.server.ServerRequestLog;
import com.x.server.console.server.Servers;

public class WebServerTools extends JettySeverTools {

	private static final Logger LOGGER = LoggerFactory.getLogger(WebServerTools.class);

	public static Server start(WebServer webServer) throws Exception {

		// 更新web服务配置信息
		WebServers.updateWebServerConfigJson();
		// 更新 favicon.ico
		updateFavicon();
		// 创建index.html
		createIndexPage();
		// copyDefaultHtml
		copyDefaultHtml();
		// 覆盖 webServer
		coverToWebServer();

		if (Objects.equals(Config.currentNode().getApplication().getPort(), webServer.getPort())) {
			return startInApplication(webServer);
		} else {
			return startStandalone(webServer);
		}

	}

	private static Server startInApplication(WebServer webServer) throws Exception {
		WebAppContext webContext = webContext(webServer);
		GzipHandler gzipHandler = (GzipHandler) Servers.applicationServer.getHandler();
		HandlerList hanlderList = (HandlerList) gzipHandler.getHandler();
		hanlderList.addHandler(webContext);
		webContext.start();
		System.out.println("****************************************");
		System.out.println("* web server is started in the application server.");
		System.out.println("* port: " + Config.currentNode().getApplication().getPort() + ".");
		System.out.println("****************************************");
		return Servers.applicationServer;
	}

	private static Server startStandalone(WebServer webServer) throws Exception {
		HandlerList handlers = new HandlerList();
		Server server = createServer(webServer, handlers);
		WebAppContext context = webContext(webServer);
		handlers.addHandler(context);
		context.start();
		if (BooleanUtils.isTrue(webServer.getProxyCenterEnable())) {
			proxyCenter(context, webServer.getProxyTimeOut());
		}
		if (BooleanUtils.isTrue(webServer.getProxyApplicationEnable())) {
			proxyApplication(context, Config.dir_store().toPath(), webServer.getProxyTimeOut());
			proxyApplication(context, Config.dir_custom().toPath(), webServer.getProxyTimeOut());
		}
		server.setDumpAfterStart(false);
		server.setDumpBeforeStop(false);
		server.setStopAtShutdown(true);
		if (BooleanUtils.isTrue(webServer.getRequestLogEnable())) {
			server.setRequestLog(requestLog(webServer));
		}
		context.setMimeTypes(Config.mimeTypes());
		server.start();
		System.out.println("****************************************");
		System.out.println("* web server start completed.");
		System.out.println("* port: " + webServer.getPort() + ".");
		System.out.println("****************************************");
		return server;
	}

	private static Server createServer(WebServer webServer, HandlerList handlers) throws Exception {
		QueuedThreadPool threadPool = new QueuedThreadPool();
		threadPool.setName("WebServerQueuedThreadPool");
		threadPool.setMinThreads(THREAD_POOL_SIZE_MIN);
		threadPool.setMaxThreads(THREAD_POOL_SIZE_MAX);
		Server server = new Server(threadPool);
		if (BooleanUtils.isTrue(webServer.getSslEnable())) {
			addHttpsConnector(server, webServer.getPort(), true);
		} else {
			addHttpConnector(server, webServer.getPort(), true);
		}
		GzipHandler gzipHandler = new GzipHandler();
		gzipHandler.setHandler(handlers);
		server.setHandler(gzipHandler);
		server.setDumpAfterStart(false);
		server.setDumpBeforeStop(false);
		server.setStopAtShutdown(true);
		server.start();
		return server;
	}

	private static WebAppContext webContext(WebServer webServer) throws IOException, URISyntaxException {
		WebAppContext context = new WebAppContext();
		context.setContextPath("/");
		context.setBaseResource(Resource.newResource(new File(Config.base(), "servers/webServer")));
		context.setParentLoaderPriority(true);
		context.setExtractWAR(false);
		context.setInitParameter("org.eclipse.jetty.servlet.Default.dirAllowed", false + "");
		context.setInitParameter("org.eclipse.jetty.servlet.Default.useFileMappedBuffer", "false");
		context.setInitParameter("org.eclipse.jetty.servlet.Default.cacheControl", "max-age=86400");
		context.setInitParameter("org.eclipse.jetty.servlet.Default.maxCacheSize", "256000000");
		context.setInitParameter("org.eclipse.jetty.servlet.Default.maxCachedFileSize", "200000000");
		context.setWelcomeFiles(new String[] { "default.html", "index.html" });
		context.setGzipHandler(new GzipHandler());
		context.setParentLoaderPriority(true);
		context.getMimeTypes().addMimeMapping("wcss", "application/json");
		if (BooleanUtils.isTrue(webServer.getStatEnable())) {
			FilterHolder statFilterHolder = new FilterHolder(new WebStatFilter());
			statFilterHolder.setInitParameter("exclusions", webServer.getStatExclusions());
			context.addFilter(statFilterHolder, "/*", EnumSet.of(DispatcherType.REQUEST));
			ServletHolder statServletHolder = new ServletHolder(StatViewServlet.class);
			statServletHolder.setInitParameter("sessionStatEnable", "false");
			context.addServlet(statServletHolder, "/druid/*");
		}
		return context;
	}

	private static RequestLog requestLog(WebServer webServer) throws Exception {
		AsyncRequestLogWriter asyncRequestLogWriter = new AsyncRequestLogWriter();
		asyncRequestLogWriter.setTimeZone(TimeZone.getDefault().getID());
		asyncRequestLogWriter.setAppend(true);
		asyncRequestLogWriter.setRetainDays(webServer.getRequestLogRetainDays());
		asyncRequestLogWriter.setFilename(
				Config.dir_logs().toString() + File.separator + "web.request.yyyy_MM_dd." + Config.node() + ".log");
		asyncRequestLogWriter.setFilenameDateFormat("yyyyMMdd");
		return new ServerRequestLog(asyncRequestLogWriter, LOG_FORMAT);
	}

	private static void proxyCenter(WebAppContext context, final Integer timeout) throws Exception {
		ServletHolder proxyHolder = new ServletHolder(Proxy.class);
		proxyHolder.setInitParameter("port", Config.currentNode().getCenter().getPort() + "");
		proxyHolder.setInitParameter("idleTimeout", timeout + "000");
		proxyHolder.setInitParameter("timeout", timeout + "000");
		context.addServlet(proxyHolder, "/" + x_program_center.class.getSimpleName() + "/*");
	}

	private static void proxyApplication(WebAppContext context, Path path, final Integer timeout) throws Exception {
		try (Stream<Path> stream = Files.list(path)) {
			stream.filter(o -> StringUtils.endsWithIgnoreCase(o.getFileName().toString(), ".war"))
					.map(Path::getFileName).map(Path::toString).map(FilenameUtils::getBaseName)
					.filter(o -> !StringUtils.equals(o, x_program_center.class.getSimpleName())).forEach(o -> {
						try {
							ServletHolder proxyHolder = new ServletHolder(Proxy.class);
							proxyHolder.setInitParameter("port", Config.currentNode().getApplication().getPort() + "");
							proxyHolder.setInitParameter("idleTimeout", timeout + "000");
							proxyHolder.setInitParameter("timeout", timeout + "000");
							context.addServlet(proxyHolder, "/" + o + "/*");
						} catch (Exception e) {
							LOGGER.error(e);
						}
					});
		}
	}

	private static void copyDefaultHtml() throws Exception {
		File file = new File(Config.dir_config(), "default.html");
		if (file.exists() && file.isFile()) {
			FileUtils.copyFile(file, new File(Config.base(), "servers/webServer/default.html"));
		}
	}

	private static void updateFavicon() throws Exception {

		File file = new File(Config.dir_config(), "favicon.ico");

		if (file.exists() && file.isFile()) {
			FileUtils.copyFile(file, new File(Config.dir_servers_webServer(), "favicon.ico"));
		}

	}

	private static void createIndexPage() throws Exception {
		if (null == Config.nodes().webServers()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<!DOCTYPE html>").append("<html>").append("<head>").append("<meta charset=\"UTF-8\">")
				.append("<title>o2 index</title>").append("</head>").append("<body>");
		for (Entry<String, WebServer> en : Config.nodes().webServers().entrySet()) {
			createIndexPagePerWebServer(sb, en);
		}
		sb.append("</body>").append("</html>");
		File file = new File(Config.base(), "index.html");
		FileUtils.write(file, sb.toString(), DefaultCharset.name);
	}

	private static void createIndexPagePerWebServer(StringBuilder sb, Entry<String, WebServer> en) {
		WebServer o = en.getValue();
		if (BooleanUtils.isTrue(o.getEnable())) {
			String url = BooleanUtils.isTrue(o.getSslEnable()) ? "https://" : "http://";
			url += en.getKey();
			if (BooleanUtils.isTrue(o.getSslEnable())) {
				url += o.getPort() != 443 ? (":" + o.getPort()) : "";
			} else {
				url += o.getPort() != 80 ? (":" + o.getPort()) : "";
			}
			sb.append("<a href=\"" + url + "\">" + url + "</a><br/>");
		}
	}

	private static void coverToWebServer() throws Exception {
		Path p = Config.path_config_coverToWebServer(true);
		if (Files.exists(p)) {
			FileUtils.copyDirectory(p.toFile(), Config.path_servers_webServer(true).toFile());
		}
	}

}
