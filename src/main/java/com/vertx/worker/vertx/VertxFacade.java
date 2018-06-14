package com.vertx.worker.vertx;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.vertx.worker.mvc.handler.RouteHandler;
import com.vertx.worker.mvc.service.BookAsyncService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.serviceproxy.ServiceProxyBuilder;

/**
 * A standard verticle, consuming to multi-thread VertxWorker{@link BookAsyncService} over the event bus.
 * This Vert.x class receives work through {@link RouteHandler}  class instance.
 * @author hyeonsang jeon
 */
@Component
public class VertxFacade extends AbstractVerticle{
	private static final Logger logger = LoggerFactory.getLogger(VertxFacade.class);

	private BookAsyncService bookAsyncService;
	
	@Override
    public void start(Future<Void> startFuture) throws Exception {		
		bookAsyncService = new ServiceProxyBuilder(vertx).setAddress(BookAsyncService.ADDRESS).build(BookAsyncService.class);
		
		startServer(
                (http) -> completeStartup(http, startFuture)
        );
	}
	
	private void startServer(Handler<AsyncResult<HttpServer>> next) {		
				
	 	Router apiRouter = Router.router(vertx);
	 	apiRouter.mountSubRouter("/book", new RouteHandler(vertx, bookAsyncService).getRouter());
	 	vertx.createHttpServer()
	 	    .requestHandler(apiRouter::accept)
	 	        .listen(prop.getInt("vertx.port"), next::handle);
	}
	
	private void completeStartup(AsyncResult<HttpServer> http, Future<Void> future) {
        if (http.succeeded()) {
        		future.complete();
        } else {
        		future.fail(http.cause());
        }
    }

	//using commons-configuration, let's see pom.xml
	static String propFileName = "application.properties";
	static Configuration prop;

	static {
		try {
			prop = new PropertiesConfiguration(propFileName);
		} catch (ConfigurationException e) {
			logger.error("VertxFacade",e);
		}
	}
	
}

