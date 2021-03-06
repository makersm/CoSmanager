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
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.InputStream;

import static org.slf4j.LoggerFactory.getLogger;


/**
 * Sample web resource.
 */
@Path("queue")
public class CoSWebResource extends AbstractWebResource {
    private final Logger log = getLogger(getClass());
    private CoSService cosService = getService(CoSService.class);

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response put(InputStream stream) {
        try {
            JsonNode jsonTree = mapper().readTree(stream);
            for (JsonNode js : jsonTree) {
                cosService.addVnidTable(js.get("vnid").toString(), js.get("cos").toString());
            }
            return Response.ok().build();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @DELETE
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{vnid}")
    public Response remove(@PathParam("vnid") String vnid) {
        try {
            cosService.deleteVnidTable(vnid);
            return Response.ok().build();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

}
