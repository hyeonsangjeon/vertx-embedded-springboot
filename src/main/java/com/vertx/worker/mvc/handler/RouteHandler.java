package com.vertx.worker.mvc.handler;

import com.vertx.worker.mvc.service.BookAsyncService;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;


/**
 * A Router, class receives work through {@link RouteHandler}  class instance.
 * @author hyeonsang jeon
 */
public class RouteHandler {

	
	private  Vertx vertx ;
	private  BookAsyncService bookAsyncService;
	
	public RouteHandler(Vertx vertx, BookAsyncService bookAsyncService) {
		this.vertx = vertx;
		this.bookAsyncService = bookAsyncService;
	}
	
	public Router getRouter() {
		RequestHandler reqHandler = new RequestHandler(bookAsyncService);
		Router router = Router.router(vertx);	
		
		router.route().handler(BodyHandler.create());
		router.route().consumes("application/json");
        router.route().produces("application/json");
		
        router.post("/add").handler(reqHandler::createBook);
        router.get("/list").handler(reqHandler::getAll);
        router.get("/id/:bookId").handler(reqHandler::get);
        router.put("/update").handler(reqHandler::updateBook);
        router.delete("/delete/:bookId").handler(reqHandler::deleteBook);
                
		return router;
	}
	
		
}
