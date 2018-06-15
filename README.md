Citrus Remote Sample ![Logo][1]
==============

This sample performs a deployment of both system under test and Citrus integration tests to a Payara application server. After the deployment is done the Citrus tests are 
executed via remote API call to the deployed Citrus test web archive. As a result the local Maven process will get the test results from the remote test execution.

The sample uses [Docker](https://www.docker.com/) for containerized application infrastructure of the Payara server instance. Both the [todo-list](../todo-app/README.md) system under test and 
the Citrus integration tests are built and run as Docker images. The Citrus tests are then able to use Docker networking and DNS features 
in order to access the exposed services on the todo-list container. This way Citrus is able to participate on the Docker infrastructure for in-container testing.

The sample uses the [Fabric8 Docker Maven plugin](https://maven.fabric8.io/) for smooth Docker integration into the Maven build lifecycle.

Read about the Citrus Docker integration in the [reference guide][4]

System under test
---------

**NOTE:** This test depends on the [todo-app](../todo-app/) as system under test. The todo-app Spring boot web application is loaded as Docker image from central Docker Hub repository beforehand.

The [todo-list](../todo-app/README.md) sample application provides a REST API as well as a SOAP API for managing todo entries. We want to access this APIs in an integration test scenario while the application is
run in Docker. This means we need to run the todo-app Docker image and start the integration tests. 

The Citrus tests are built and run as container, too. The Citrus test container is then able to connect with the todo-list API via Docker networking between containers. Also
with Citrus Docker client integration we can check and manipulate the todo-app Docker container at test runtime.

Citrus remote web archive
---------

This time we do not use Maven surefire and failsafe plugin for executing the Citrus integration tests locally. Instead we build an executable 
test webapp WAR with all dependencies. This web archive contains Citrus and all tests in this project being able to execute all tests as part of a normal web application deployment. First of all we add a dependency to the `citrus-remote-server`
library that is able to execute Citrus tests as part of a web application deployment.
    
```xml
<dependency>
  <groupId>com.consol.citrus</groupId>
  <artifactId>citrus-remote-server</artifactId>
  <version>${citrus.version}</version>
  <scope>test</scope>
</dependency>
```

After that we tell Maven to also create a WAR web archive during the build. The test-war artifact will hold all test sources and all test scoped dependencies of the current Maven project.

```xml
<plugin>
  <groupId>com.consol.citrus</groupId>
  <artifactId>citrus-remote-maven-plugin</artifactId>
  <version>${project.version}</version>
  <executions>
    <execution>
      <goals>
        <goal>test-war</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

After that you should find a new WAR file with classifier `-citrus-tests.war` in the Maven `target` build output folder. We will use this WAR as deployable artifact that executes the tests as a web application on the Payara server.

The sample uses the Fabric8 Docker Maven plugin for building and running Docker images. So lets take a deep dive into the Maven POM configuration.
  
```xml
<plugin>
  <groupId>io.fabric8</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>${docker.maven.plugin.version}</version>
  <configuration>
    <verbose>true</verbose>
    <watchInterval>1000</watchInterval>
    <images>
      <image>
        <alias>todo-app</alias>
        <name>citrusframework/todo-demo-app:${todo.app.version}</name>
        <watch>
          <mode>none</mode>
        </watch>
        <run>
          <namingStrategy>alias</namingStrategy>
          <ports>
            <port>8080:8080</port>
          </ports>
          <wait>
            <http>
              <url>http://localhost:8080/todolist</url>
              <method>GET</method>
              <status>200</status>
            </http>
            <time>60000</time>
            <shutdown>500</shutdown>
          </wait>
          <log>
            <enabled>true</enabled>
            <color>green</color>
          </log>
        </run>
      </image>
      <image>
        <alias>todo-app-tests</alias>
        <name>citrus/todo-app-tests:${project.version}</name>
        <build>
          <dockerFile>Dockerfile</dockerFile>
          <assembly>
            <inline>
              <files>
                <file>
                  <source>${project.build.directory}/${project.artifactId}-${project.version}-citrus-tests.war</source>
                  <destName>tests.war</destName>
                  <outputDirectory>.</outputDirectory>
                </file>
              </files>
            </inline>
          </assembly>
        </build>
        <run>
          <namingStrategy>alias</namingStrategy>
          <ports>
            <port>8088:8080</port>
            <port>4848:4848</port>
          </ports>
          <links>
            <link>todo-app</link>
          </links>
          <dependsOn>
            <dependsOn>todo-app</dependsOn>
          </dependsOn>
          <wait>
            <http>
              <url>http://localhost:8088/citrus-integration-tests/health</url>
              <method>GET</method>
              <status>200</status>
            </http>
            <time>60000</time>
            <shutdown>500</shutdown>
          </wait>
          <log>
            <enabled>false</enabled>
            <color>cyan</color>
          </log>
        </run>
      </image>
    </images>
  </configuration>
</plugin>
```

Wow that is lots of configuration. Let us understand this step by step. First of all we take a look at the todo-app system under test image build configuration:

```xml
<image>
    <alias>todo-app</alias>
    <name>citrusframework/todo-demo-app:${todo.app.version}</name>
    ...
</image>    
```

This will automatically pull the Docker image `citrusframework/todo-demo-app` in its tagged version from Docker Hub repository:

The second image in the configuration is the Citrus test remote web archive image that we built from the current project:

```xml
<build>
  <dockerFile>Dockerfile</dockerFile>
  <assembly>
    <inline>
      <files>
        <file>
          <source>${project.build.directory}/${project.artifactId}-${project.version}-citrus-tests.war</source>
          <destName>tests.war</destName>
          <outputDirectory>.</outputDirectory>
        </file>
      </files>
    </inline>
  </assembly>
</build>
```

We build a new Docker image using the Dockerfile. This Dockerfile uses the `FROM payara/server-full:181` Payara server base image. The Dockerfile also deploys the Citrus remote test WAR that we created earlier in the Maven build lifecycle.

Docker build
---------

You can build the Docker image with:

```
mvn docker:build
```

Of course you need a running Docker installation on your host. After that you should see two new images built seconds ago:

```
docker images

REPOSITORY                      TAG                  IMAGE ID            CREATED             SIZE
citrus/todo-app-tests           1.0.6                0e403393d805        5 minutes ago       959MB
citrusframework/todo-demo-app   1.1.0                15293d924dae        34 minutes ago      699MB
```

So we are now ready to run the images as containers in Docker. If you inspect the Docker Maven plugin configuration you will find run sections that describe how the containers will be started.

```xml
<image>
    <alias>todo-app</alias>
    <name>citrusframework/todo-demo-app:${todo.app.version}</name>
    ...
    <run>
      <namingStrategy>alias</namingStrategy>
      <ports>
        <port>8080:8080</port>
      </ports>
      <wait>
        <http>
          <url>http://localhost:8080/todolist</url>
          <method>GET</method>
          <status>200</status>
        </http>
        <time>60000</time>
        <shutdown>500</shutdown>
      </wait>
      <log>
        <enabled>true</enabled>
        <color>green</color>
      </log>
    </run>
</image>
```

When the todo-app container is started we expose port *8080* for clients. This is the default port. In addition to that we tell the Docker Maven plugin
to wait for the application to start up. This is done by probing the http url *http://localhost:8080/todolist* with a *Http GET* request. Once the application is
started and Tomcat is ready we can start the Citrus test container.

```xml
<image>
  <alias>todo-app-tests</alias>
  <name>citrus/todo-app-tests:${project.version}</name>
  ...
  <run>
    <namingStrategy>alias</namingStrategy>
    <ports>
      <port>8088:8080</port>
      <port>4848:4848</port>
    </ports>
    <links>
      <link>todo-app</link>
    </links>
    <dependsOn>
      <dependsOn>todo-app</dependsOn>
    </dependsOn>
    <wait>
      <http>
        <url>http://localhost:8088/citrus-integration-tests/health</url>
        <method>GET</method>
        <status>200</status>
      </http>
      <time>60000</time>
      <shutdown>500</shutdown>
    </wait>
    <log>
      <enabled>false</enabled>
      <color>cyan</color>
    </log>
  </run>
</image>
```

This time the configuration links the container to the *todo-app* container. This enabled the Docker networking feature where DNS host resolving will
be available for the Citrus test container. Exposed ports (8080) in the *todo-app* container are then accessible. 

The test-war is using the context path `/tests`. After the deployment we can trigger the Citrus test execution on that server with:

```bash
http://localhost:8080/tests/run
```
  
This test execution can be bound to the Maven lifecycle via citrus-remote-maven-plugin:

```xml
<plugin>
  <groupId>com.consol.citrus</groupId>
  <artifactId>citrus-remote-maven-plugin</artifactId>
  <version>${citrus.version}</version>
  <executions>
    <execution>
      <goals>
        <goal>test-war</goal>
        <goal>test</goal>
        <goal>verify</goal>
      </goals>
      <configuration>
        <server>
          <url>http://localhost:8088/citrus-integration-tests</url>
        </server>
        <run>
          <packages>
            <package>com.consol.citrus.samples.payara</package>
          </packages>
        </run>
      </configuration>
    </execution>
  </executions>
</plugin>
```

The `run` goal is bound to the `integration-test` lifecycle phase in out Maven build. All deployed Citrus test cases will execute in the WAR deployment.

The Citrus http client uses the following endpoint url for accessing the REST API:

```java
@Bean
public HttpClient todoClient() {
    return CitrusEndpoints.http()
                        .client()
                        .requestUrl("http://todo-app:8080")
                        .build();
}
```

As you can see the client will be able to resolve the hostname *todo-app* via Docker networking feature. The test may then access the REST API over http in order to
add new todo items.

```java
http()
    .client(todoClient)
    .send()
    .post("/api/todolist")
    .messageType(MessageType.JSON)
    .contentType("application/json")
    .payload("{ \"id\": \"${todoId}\", \"title\": \"${todoName}\", \"description\": \"${todoDescription}\", \"done\": ${done}}");

http()
    .client(todoClient)
    .receive()
    .response(HttpStatus.OK)
    .messageType(MessageType.PLAINTEXT)
    .payload("${todoId}");
```

Docker run
---------

No finally lets start the Docker containers with:

```
mvn docker:start
```

You will see that the Docker Maven plugin first of all is starting the *todo-app* Tomcat container with Spring Boot running. After that the Citrus test container is
started to perform all integration tests. You will see the Citrus logging output. The Docker Maven plugin waits for the *BUILD SUCCESS* log entry that marks that all tests
were executed successfully.

You can access the log output also by

```
docker logs todo-app-tests
```

Once the build is finished the Citrus test container is automatically stopped but the container is still there. You can see the container with:

```
docker ps -a

CONTAINER ID   IMAGE                                 COMMAND                  CREATED          STATUS              PORTS                                                                NAMES
c5173f514448   citrus/todo-app-tests:1.0.6           "/bin/sh -c '${PAYAR…"   47 seconds ago   Up 52 seconds       8009/tcp, 0.0.0.0:4848->4848/tcp, 8181/tcp, 0.0.0.0:8088->8080/tcp   todo-app-tests
38c51a9a12f9   citrusframework/todo-demo-app:1.1.0   "/bin/sh -c 'java -D…"   56 seconds ago   Up About a minute   0.0.0.0:8080->8080/tcp, 61616/tcp                                    todo-app
```

Once the Docker containers are running you can access the todo-app web UI:

```
http://localhost:8080/todolist
```

The Citrus remote test web archive also provides a web interface:

```
http://localhost:8088/citrus-integration-tests/health
http://localhost:8088/citrus-integration-tests/configuration
http://localhost:8088/citrus-integration-tests/results/suite
http://localhost:8088/citrus-integration-tests/results
```

Docker stop
---------

With `mvn docker:stop` we can stop and cleanup all containers.
 
Lets add a Maven configuration that binds the Docker plugin goals to the integration-test build phase for automated Docker build, setup, start, test and stop.
  
```xml
<plugin>
  <groupId>io.fabric8</groupId>
  <artifactId>docker-maven-plugin</artifactId>
  <version>${docker.maven.plugin.version}</version>
  <executions>
    <execution>
      <id>start</id>
      <phase>pre-integration-test</phase>
      <goals>
        <goal>build</goal>
        <goal>start</goal>
      </goals>
    </execution>
    <execution>
      <id>stop</id>
      <phase>post-integration-test</phase>
      <goals>
        <goal>stop</goal>
      </goals>
    </execution>
  </executions>
  ...
</plugin> 
```  

Now we are ready to call `mvn clean install` and everything is done automatically. We get a successful build when everything worked and
in case a Citrus test failed we get a failed build. This Maven build is perfectly working with continuous integration on Jenkins for instance.
     
Further information
---------

For more information on Citrus see [www.citrusframework.org][2], including
a complete [reference manual][3].

 [1]: https://www.citrusframework.org/img/brand-logo.png "Citrus"
 [2]: https://www.citrusframework.org
 [3]: https://www.citrusframework.org/reference/html/
 [4]: https://www.citrusframework.org/reference/html#docker
