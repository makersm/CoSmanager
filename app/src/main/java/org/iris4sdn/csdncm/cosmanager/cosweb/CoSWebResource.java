/*
 * Copyright 2014-2015 Open Networking Laboratory
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
package org.iris4sdn.csdncm.cosmanager.cosweb;

import com.fasterxml.jackson.databind.JsonNode;
import org.iris4sdn.csdncm.cosmanager.CoSService;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.io.InputStream;

import static org.slf4j.LoggerFactory.getLogger;


/**
 * Sample web resource.
 */
@Path("cos")
public class CoSWebResource extends AbstractWebResource {
    private final Logger log = getLogger(getClass());
    private CoSService cosService = getService(CoSService.class);

    @POST
    @Path("addVnidTable")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(InputStream stream) {
        try {
            JsonNode jsonTree = mapper().readTree(stream);
            for(JsonNode js  : jsonTree) {
                cosService.addVnidTable(js.get("vnid").asInt(), js.get("cos").asInt());
            }
            return Response.ok().build();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @POST
    @Path("deleteVnidTable")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response remove(InputStream stream) {
        try {
            JsonNode jsonTree = mapper().readTree(stream);
            for(JsonNode js  : jsonTree) {
                cosService.deleteVnidTable(js.get("vnid").asInt());
            }
            return Response.ok().build();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
