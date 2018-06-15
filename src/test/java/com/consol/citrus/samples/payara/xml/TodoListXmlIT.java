/*
 * Copyright 2006-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.consol.citrus.samples.payara.xml;

import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.dsl.testng.TestNGCitrusTestDesigner;
import com.consol.citrus.http.client.HttpClient;
import com.consol.citrus.message.MessageType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.testng.annotations.Test;

/**
 * @author Christoph Deppisch
 */
public class TodoListXmlIT extends TestNGCitrusTestDesigner {

    @Autowired
    private HttpClient todoClient;

    @Test
    @CitrusTest
    public void testXmlDomTreeValidation() {
        variable("todoId", "citrus:randomUUID()");
        variable("todoName", "citrus:concat('todo_', citrus:randomNumber(4))");
        variable("todoDescription", "Description: ${todoName}");

        http()
            .client(todoClient)
            .send()
            .post("/api/todolist")
            .contentType("application/xml")
            .payload("<todo xmlns=\"http://citrusframework.org/samples/todolist\">" +
                        "<id>${todoId}</id>" +
                        "<title>${todoName}</title>" +
                        "<description>${todoDescription}</description>" +
                    "</todo>");

        http()
            .client(todoClient)
            .receive()
            .response(HttpStatus.OK)
            .messageType(MessageType.PLAINTEXT)
            .payload("${todoId}");

        http()
            .client(todoClient)
            .send()
            .get("/api/todo/${todoId}")
            .accept("application/xml");

        http()
            .client(todoClient)
            .receive()
            .response(HttpStatus.OK)
            .payload("<todo xmlns=\"http://citrusframework.org/samples/todolist\">" +
                        "<id>${todoId}</id>" +
                        "<title>${todoName}</title>" +
                        "<description>${todoDescription}</description>" +
                        "<done>false</done>" +
                    "</todo>");
    }

    @Test
    @CitrusTest
    public void testXmlValidationWithFileResource() {
        variable("todoId", "citrus:randomUUID()");
        variable("todoName", "citrus:concat('todo_', citrus:randomNumber(4))");
        variable("todoDescription", "Description: ${todoName}");

        http()
            .client(todoClient)
            .send()
            .post("/api/todolist")
            .contentType("application/xml")
            .payload(new ClassPathResource("templates/todo.xml"));

        http()
            .client(todoClient)
            .receive()
            .response(HttpStatus.OK)
            .messageType(MessageType.PLAINTEXT)
            .payload("${todoId}");

        http()
            .client(todoClient)
            .send()
            .get("/api/todo/${todoId}")
            .accept("application/xml");

        http()
            .client(todoClient)
            .receive()
            .response(HttpStatus.OK)
            .payload(new ClassPathResource("templates/todo.xml"));
    }

    @Test
    @CitrusTest
    public void testXpathValidation() {
        variable("todoId", "citrus:randomUUID()");
        variable("todoName", "citrus:concat('todo_', citrus:randomNumber(4))");
        variable("todoDescription", "Description: ${todoName}");

        http()
            .client(todoClient)
            .send()
            .post("/api/todolist")
            .contentType("application/xml")
            .payload("<todo xmlns=\"http://citrusframework.org/samples/todolist\">" +
                        "<id>${todoId}</id>" +
                        "<title>${todoName}</title>" +
                        "<description>${todoDescription}</description>" +
                    "</todo>");

        http()
            .client(todoClient)
            .receive()
            .response(HttpStatus.OK)
            .messageType(MessageType.PLAINTEXT)
            .payload("${todoId}");

        http()
            .client(todoClient)
            .send()
            .get("/api/todo/${todoId}")
            .accept("application/xml");

        http()
            .client(todoClient)
            .receive()
            .response(HttpStatus.OK)
            .validate("/t:todo/t:id", "${todoId}")
            .validate("/t:todo/t:title", "${todoName}")
            .validate("/t:todo/t:description", "${todoDescription}")
            .validate("/t:todo/t:done", "false");
    }

}
