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
package org.iris4sdn.csdncm.cosmanager.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.iris4sdn.csdncm.cosmanager.CoSService;
import org.iris4sdn.csdncm.vxlanflowmapper.DefaultOuterPacket;
import org.iris4sdn.csdncm.vxlanflowmapper.InnerPacket;
import org.iris4sdn.csdncm.vxlanflowmapper.MapperEvent;
import org.iris4sdn.csdncm.vxlanflowmapper.MapperListener;
import org.iris4sdn.csdncm.vxlanflowmapper.OuterPacket;
import org.iris4sdn.csdncm.vxlanflowmapper.VxlanFlowMappingService;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.EventuallyConsistentMapEvent;
import org.onosproject.store.service.EventuallyConsistentMapListener;
import org.onosproject.store.service.LogicalClockService;
import org.onosproject.store.service.StorageService;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
@Service
public class CoSManager implements CoSService {
    private final Logger log = getLogger(getClass());
    private static final String APP_ID = "org.iris4sdn.csdncm.cosmanager";
    public EventuallyConsistentMap<Integer, Integer> cosStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LogicalClockService clockService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VxlanFlowMappingService vxlanFlowMappingService;


    private ApplicationId appId;

    private MapperListener mapperListener = new InnerMapperListener();
    private CoSListener cosListener = new CoSListener();
    private static CoSRuleInstaller installer;
    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_ID);
        installer = CoSRuleInstaller.ruleInstaller(appId);

        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(String.class)
                .register(Integer.class);

        cosStore = storageService
                .<Integer, Integer>eventuallyConsistentMapBuilder()
               .withName("cosMap").withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        cosStore.addListener(cosListener);
        vxlanFlowMappingService.addListener(mapperListener);

        log.info("-------------!{} started!--------------", appId.id());
    }

    @Deactivate
    public void deactivate(){
        cosStore.removeListener(cosListener);
        log.info("-----------!EXTERMINATE!-------------");
    }

    @Override
    public void addVnidTable(int vnid, int cos) {
        cosStore.put(vnid, cos);
    }

    @Override
    public void deleteVnidTable(int vnid) {
        cosStore.remove(vnid);
    }

    @Override
    public Set<Integer> getVnidkeySet() {
        return cosStore.keySet();
    }

    @Override
    public int getVnidValue(int vnid) {
        return cosStore.get(vnid);
    }


    public class CoSListener implements EventuallyConsistentMapListener<Integer, Integer> {
        @Override
        public void event(EventuallyConsistentMapEvent<Integer, Integer> eventuallyConsistentMapEvent) {
            if(eventuallyConsistentMapEvent.type() == EventuallyConsistentMapEvent.Type.PUT)
                log.info("Put data : vnid) {} cos) {}", eventuallyConsistentMapEvent.key(), eventuallyConsistentMapEvent.value());
            if(eventuallyConsistentMapEvent.type() == EventuallyConsistentMapEvent.Type.REMOVE)
                log.info("Remove data : vnid) {} cos) {}", eventuallyConsistentMapEvent.key(), eventuallyConsistentMapEvent.value());
        }
    }


    private void processMapper(String outerPacket, InnerPacket innerPacket, Objective.Operation type) {
        log.info("processMapper : vid {}",innerPacket.vnid());
        if(cosStore.containsKey(innerPacket.vnid())){
            Map<String, String> out = DefaultOuterPacket.decodeStringPacket(outerPacket);
            installer.enqueue(out, cosStore.get(innerPacket.vnid()),type);
        }
    }

    public class InnerMapperListener implements MapperListener {
        @Override
        public void event(MapperEvent event) {
            EventuallyConsistentMapEvent<String, InnerPacket> mapper = event.subject();
            if (MapperEvent.Type.MAPPER_PUT == event.type()) {
                processMapper(mapper.key(), mapper.value(), Objective.Operation.ADD);
            } else if (MapperEvent.Type.MAPPER_REMOVE == event.type()) {
                processMapper(mapper.key(), mapper.value(), Objective.Operation.REMOVE);
            }
        }
    }
}
