package com.vertx.worker.vertx;

import com.vertx.worker.mvc.handler.RouteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.vertx.worker.mvc.service.BookAsyncService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.serviceproxy.ServiceBinder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.*;


@Component
/**
 * Multi-thread worker verticles, this vert.x class consumming {@link BookAsyncService} over the event bus.
 * @author hyeonsang jeon
 */
@Scope(SCOPE_PROTOTYPE)
public class VertxWorker extends AbstractVerticle{
	private static final Logger logger = LoggerFactory.getLogger(VertxWorker.class);
	 
	@Autowired	
	BookAsyncService bookAsyncService;

	@Override	
	public void start(Future<Void> startFuture) throws Exception {		
		new ServiceBinder(vertx).setAddress(BookAsyncService.ADDRESS).register(BookAsyncService.class, bookAsyncService).completionHandler(ar ->{			
			if (ar.succeeded()) {
				logger.info("SpringWorker started");
				startFuture.complete();
			} else {
				startFuture.fail(ar.cause());
			}
	    });
	}
}
