package com.vertx.worker.mvc.handler;

import java.util.Date;
import io.vertx.core.http.HttpServerResponse;
import org.springframework.stereotype.Component;
import com.vertx.worker.mvc.dto.Book;
import com.vertx.worker.mvc.service.BookAsyncService;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
/**
 * Facade sender class, sending to {@link BookAsyncService} requested Object.
 * @author hyeonsang jeon
 */
@Component
public class RequestHandler {

	public RequestHandler(BookAsyncService bookAsyncService) {
		this.bookAsyncService = bookAsyncService;
	}

	private BookAsyncService bookAsyncService;

	//Create Book
	public void createBook(RoutingContext routingContext) {
        JsonObject reqParam  = routingContext.getBodyAsJson();

		bookAsyncService.save(reqParam, ar -> {
		    if (ar.succeeded()) {
		    	JsonObject result = ar.result();
		    	this.sendResult(routingContext.response(),result);
		    } else {
		    	routingContext.fail(ar.cause());
		    }
		});
	}
	
		
	//Read All Book List
	public void getAll(RoutingContext routingContext) {

		bookAsyncService.getAll(ar -> {
			if (ar.succeeded()) {
				JsonObject result = ar.result();
				this.sendResult(routingContext.response(),result);
		    } else {
				routingContext.fail(ar.cause());
		    }
		});
	}
	
	
	//Read One Book
	public void get(RoutingContext routingContext) {

		HttpServerRequest request  = routingContext.request();
		Long bookId = Long.parseLong(request.params().get("bookId"));

		bookAsyncService.get(bookId, ar -> {
			if(ar.succeeded()) {

				JsonObject result = ar.result();
				this.sendResult(routingContext.response(),result);
			} else {							
				routingContext.fail(ar.cause());
			}			
		});
	}
	
	//Update Book
	public void updateBook(RoutingContext routingContext) {	
		
		Book reqBook = new Book(routingContext.getBodyAsJson());

		bookAsyncService.get(reqBook.getId(), ar1st ->{
			if(ar1st.succeeded() && null!=ar1st.result().getJsonObject("data").getInteger("id")){
				bookAsyncService.update(reqBook, ar2nd -> {
					if (ar2nd.succeeded()) {
						this.sendResult(routingContext.response(),ar2nd.result());
					}
				});
			}else{
				this.sendResult(routingContext.response(),ar1st.result());
			}
		});

	}
	
	//Delete Book
	public void deleteBook(RoutingContext routingContext) {						
//		Long bookId = routingContext.getBodyAsJson().getLong("id");

		HttpServerRequest request  = routingContext.request();
		Long bookId = Long.parseLong(request.params().get("bookId"));

		bookAsyncService.get(bookId, ar1st ->{
			if(ar1st.succeeded()&& null!=ar1st.result().getJsonObject("data").getInteger("id")){
				bookAsyncService.delete(bookId, ar2nd -> {
					if (ar2nd.succeeded()) {
						JsonObject result = ar2nd.result();
						result.put("data",ar1st.result().getJsonObject("data"));
						this.sendResult(routingContext.response(),result);
					}
				});
			}else{
				this.sendResult(routingContext.response(),ar1st.result());
			}

		});

	}

	//response method
    private void sendResult(HttpServerResponse response, JsonObject result) {
        int statusCode = result.getInteger("statusCode");
        response.putHeader("content-type", "application/json");
        response.putHeader("Access-Control-Allow-Origin", "*");
        response.putHeader("X-Application-Context", "application");
        response.putHeader("Date", new Date().toString());
        response.setChunked(true);
        response.setStatusCode(statusCode);
        response.end(result.encodePrettily());
    }

}
