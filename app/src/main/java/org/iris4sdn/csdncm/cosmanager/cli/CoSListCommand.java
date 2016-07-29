/*
 * Copyright 2014 Open Networking Laboratory
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
package org.iris4sdn.csdncm.cosmanager.cli;

import org.apache.karaf.shell.commands.Command;
import org.iris4sdn.csdncm.cosmanager.CoSService;
import org.onosproject.cli.AbstractShellCommand;

/**
 * Sample Apache Karaf CLI command
 */
@Command(scope = "onos", name = "cos-list-show",
        description = "Sample Apache Karaf CLI command")
public class CoSListCommand extends AbstractShellCommand {

    CoSService cosService = getService(CoSService.class);

    @Override
    protected void execute() {
        for(int key : cosService.getVnidkeySet() ){
            print(String.format("vnid : %d, cos : %s", key, cosService.getVnidValue(key)) );
        }
    }

}
