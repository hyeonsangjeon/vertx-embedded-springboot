Vert.x embeded Springboot
===

This example can be used to distributed processing in JAVA when you need asyncronous multi thread worker pattern., having:

- Vert.x Worker threads verticle example
- SpringBoot Liquebase, init DB data migration when container deploying. 
- SpringBoot Actuator, process status check
- Vert.x SpringBoot JPA example, SQL.
- Vert.x SpringBoot with Mybatis, SQL.

##### This example works in the following order.
1. liquibase DB migration to DataBase(H2 or MariaDB) when instance have deployed
2. Migration Status Check at Spring Actuator
3. Rest API Check to Vert.x Facade verticle (Request & Response)
4. Check worker thread logging in console logging. (initial set : worker verticle instance 4, Event Loop : 6 ) 
5. Check Mybatis sql logging 

##### port info 
|port           |Description                                                           |
|---------------|----------------------------------------------------------------------|
|8989           |Vert.x communication port, it is used when communicate to Facade normal verticle which works request and response to client|
|9000           |Springboot embeded port, it is used when mybatis jdbc connection pool |
|7979           |Springboot actuator port, it is used service instance monitoring      |

##### To try the example, 

```console
mvn clean spring-boot:run -P h2local
```
or, you can build to profile mariadb after change application.properties in mariadb profile
```console
mvn clean spring-boot:run -P mariadb
``` 

##### Let's check initail migration 
id : bookexample, 
pw : 1234 
```console
http://localhost:7979/actuator
http://localhost:7979/actuator/liquibase
```

##### To try request rest api
In console log, you can see different worker thread works in concrete class instance works as [worker-thread-0] ~ [worker-thread-8]
 
- book list search
```console
curl --request GET --url http://localhost:8989/book/list
``` 

- book add 
```console
curl -X POST http://localhost:8989/book/add   
-d '{
  "name":"marble comics",
  "author":"marble",
  "pages":987
}'
``` 

-  book search one 
```console
curl -X GET http://localhost:8989/book/id/1
```

- book delete   
```console
curl -X DELETE http://localhost:8989/book/delete/2
```

## Reference
1. https://vertx.io/docs/vertx-core/java/
2. https://github.com/vert-x3/



