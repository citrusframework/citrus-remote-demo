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

package com.consol.citrus.samples.payara.soap;

import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.dsl.testng.TestNGCitrusTestDesigner;
import com.consol.citrus.http.client.HttpClient;
import com.consol.citrus.ws.client.WebServiceClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.testng.annotations.Test;

/**
 * @author Christoph Deppisch
 */
public class TodoListSoapIT extends TestNGCitrusTestDesigner {

    @Autowired
    private HttpClient todoClient;

    @Autowired
    private WebServiceClient todoSoapClient;

    @Test
    @CitrusTest
    public void testAddTodoEntry() {
        variable("todoName", "citrus:concat('todo_', citrus:randomNumber(4))");
        variable("todoDescription", "Description: ${todoName}");

        clearTodoList();

        soap()
            .client(todoSoapClient)
            .send()
            .soapAction("addTodoEntry")
            .payload(new ClassPathResource("templates/addTodoEntryRequest.xml"));

        soap()
            .client(todoSoapClient)
            .receive()
            .payload(new ClassPathResource("templates/addTodoEntryResponse.xml"));

        soap()
            .client(todoSoapClient)
            .send()
            .soapAction("getTodoList")
            .payload(new ClassPathResource("templates/getTodoListRequest.xml"));

        soap()
            .client(todoSoapClient)
            .receive()
            .payload(new ClassPathResource("templates/getTodoListResponse.xml"));
    }

    /**
     * Remove all entries from todolist.
     */
    private void clearTodoList() {
        http()
                .client(todoClient)
                .send()
                .delete("/todolist");

        http()
                .client(todoClient)
                .receive()
                .response(HttpStatus.FOUND);
    }

}
