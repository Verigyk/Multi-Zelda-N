We are using JDK 25, 
Install Spring Boot Extension Pack, Extension Pack For Java

We use Spring Boot 4.0.5 and dependences
— Spring Data JPA
— HyperSQL Database

We are using hsqldb 2.7.2.
Please run the database before compiling and running your backend

We implemented an authentification system from these websites :
https://websockets.readthedocs.io/en/stable/topics/authentication.html
https://dev.to/realnamehidden1_61/how-to-use-jwt-authentication-in-spring-boot-java-21-an-end-to-end-beginner-guide-3pc4
https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/Cookies
https://medium.com/@dasbabai2017/managing-cookies-in-spring-boot-a-practical-guide-with-code-ae7ec37d918f
https://dev.to/bcerati/les-cookies-httponly-une-securite-pour-vos-tokens-2p8n

## How can you launch the website?

Download the Apache Tomcat HTTP server :  
https://moodle.inp-toulouse.fr/mod/resource/view.php?id=84202 

## First, launch the database

Execute the following command in the database file
```
cd DataBase
source start.sh
```

## Then, launch the backend


In order to launch your backend, move into the following directory :
```
cd Backend/facade
```

If you want to compile your backend :   
```
./mvnw package
```
If you haven't launched the database, backend won't compile    
   
Copy .war file into your Apache Tomcat directory :
```
cp target/facade-0.0.1-SNAPSHOT.war {apache_directory}/webapps/facade.war
```

## Now, you can launch the frontend

Move into your frontend directory :
```
cd Frontend
```

Compile your frontend :
```
source comp.sh Controller_View
```

Copy .war file into your Apache Tomcat directory 
 ```
 cp ./Controller_View.war {apache_directory}/webapps/Controller_View.war
 ```

Now you can enjoy the game!
