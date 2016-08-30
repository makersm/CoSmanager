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
import org.iris4sdn.csdncm.vxlanflowmapper.VxlanFlowMappingService;
import org.iris4sdn.csdncm.vxlanflowmapper.VxlanPacketParser;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.Path;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flowobjective.Objective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.topology.TopologyService;
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
    private static final Logger log = getLogger(CoSManager.class);
    private static final String APP_ID = "org.iris4sdn.csdncm.cosmanager";
    public EventuallyConsistentMap<Integer, Integer> vnidRuleStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected LogicalClockService clockService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VxlanFlowMappingService vxlanFlowMappingService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    private ApplicationId appId;

    private MapperListener vxlanMapperListener = new InnerVxlanMapperListener();
    private CoSListener vnidRuleListener = new CoSListener();
    private CoSPacketProcessor processor = new CoSPacketProcessor();
    private static CoSRuleInstaller installer;

    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_ID);
        installer = CoSRuleInstaller.ruleInstaller(appId);
        packetService.addProcessor(processor, PacketProcessor.director(2));

        KryoNamespace.Builder serializer = KryoNamespace.newBuilder()
                .register(String.class)
                .register(Integer.class);

        vnidRuleStore = storageService
                .<Integer, Integer>eventuallyConsistentMapBuilder()
                .withName("VnidRuleMap").withSerializer(serializer)
                .withTimestampProvider((k, v) -> clockService.getTimestamp())
                .build();

        vnidRuleStore.addListener(vnidRuleListener);
        vxlanFlowMappingService.addListener(vxlanMapperListener);

        log.info("-------------!{} started!--------------", appId.id());
    }

    @Deactivate
    public void deactivate() {
        vnidRuleStore.removeListener(vnidRuleListener);
        vxlanFlowMappingService.removeListener(vxlanMapperListener);
        processor = null;
        log.info("-----------!EXTERMINATE!-------------");
    }

    @Override
    public void addVnidTable(int vnid, int cos) {
        vnidRuleStore.put(vnid, cos);
    }

    @Override
    public void deleteVnidTable(int vnid) {
        vnidRuleStore.remove(vnid);
    }

    @Override
    public Set<Integer> getVnidkeySet() {
        return vnidRuleStore.keySet();
    }

    @Override
    public int getVnidValue(int vnid) {
        return vnidRuleStore.get(vnid);
    }


    public class CoSListener implements EventuallyConsistentMapListener<Integer, Integer> {
        @Override
        public void event(EventuallyConsistentMapEvent<Integer, Integer> eventuallyConsistentMapEvent) {
            if (eventuallyConsistentMapEvent.type() == EventuallyConsistentMapEvent.Type.PUT) {
                log.info("Put data : vnid) {} cos) {}", eventuallyConsistentMapEvent.key(), eventuallyConsistentMapEvent.value());
            }
            if (eventuallyConsistentMapEvent.type() == EventuallyConsistentMapEvent.Type.REMOVE) {
                log.info("Remove data : vnid) {} cos) {}", eventuallyConsistentMapEvent.key(), eventuallyConsistentMapEvent.value());
            }
        }
    }

    private void processMapper(String outerPacket, InnerPacket innerPacket, Objective.Operation type) {
        log.info("processMapper : vid {}", innerPacket.vnid());
        if (vnidRuleStore.containsKey(innerPacket.vnid())) {
            Map<String, String> out = DefaultOuterPacket.decodeStringPacket(outerPacket);
            //TODO generate cos rule to fwd
            //installer.enqueue(out, vnidRuleStore.get(innerPacket.vnid()), type);
        }
    }

    private void sth(Map<String, String> sh, PortNumber portNumber, Objective.Operation type) {
        int packetVnid = Integer.parseInt(sh.get("vnid"));
        if (vnidRuleStore.containsKey(packetVnid)) {
            installer.enqueue(sh, vnidRuleStore.get(packetVnid), portNumber, type);
        }
    }

    /**
     * packetStorage Listener in vxlanflowmapper
     */
    public class InnerVxlanMapperListener implements MapperListener {
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

    private Path pickForwardPathIfPossible(Set<Path> paths, PortNumber notToPort) {
        Path lastPath = null;
        for (Path path : paths) {
            lastPath = path;
            if (!path.src().port().equals(notToPort)) {
                return path;
            }
        }
        return lastPath;
    }

    private PortNumber checkFloodPoint(PacketContext context) {
        if (topologyService.isBroadcastPoint(topologyService.currentTopology(),
                                             context.inPacket().receivedFrom())) {
            return PortNumber.FLOOD;
        }
        return null;
    }

    private PortNumber getPortNumPath(PacketContext context) throws NullPointerException {
        try {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();

            if (ethPkt.getDestinationMAC() == null) {
                return null;
            }

            HostId id = HostId.hostId(ethPkt.getDestinationMAC());
            log.info("get port num : id {}", id);
            if (id.mac().isLinkLocal()) {
                return null;
            }

            Host dst = hostService.getHost(id);
            log.info("get port num : dst {}", dst);
            if (dst == null) {
                return null;
            }

            if (pkt.receivedFrom().deviceId().equals(dst.location().deviceId())) {
                if (!context.inPacket().receivedFrom().port().equals(dst.location().port())) {
                    return dst.location().port();
                }
            }

            Set<Path> paths =
                    topologyService.getPaths(topologyService.currentTopology(),
                                             pkt.receivedFrom().deviceId(),
                                             dst.location().deviceId());
            if (paths.isEmpty()) {
                return checkFloodPoint(context);
            }

            Path path = pickForwardPathIfPossible(paths, pkt.receivedFrom().port());

            if (path == null) {
                return checkFloodPoint(context);
            }
            return path.src().port();
        } catch (NullPointerException e) {
            return null;
        }

        // Otherwise forward and be done with it.

    }

    private class CoSPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
             /* Outer packet */
            Ethernet ethernet = pkt.parsed();
            if (ethernet == null) {
                return;
            }
            if (ethernet.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipPacket = (IPv4) ethernet.getPayload();
                if (ipPacket.getProtocol() == IPv4.PROTOCOL_UDP) {
                    Map<String, String> parse = VxlanPacketParser.packetParsed(ethernet);

                    log.info(parse.toString());

                    PortNumber port = getPortNumPath(context);

                    Integer vnid = Integer.parseInt(parse.get("vnid"));
                    log.info("CoSPacketProcessor : {}", vnid);

                    if(port == null || !vnidRuleStore.containsKey(vnid)) {
                        log.info("no proper port or CoS");
                        return;
                    }

                    installer.enqueue(parse, context.inPacket().receivedFrom().port(),
                                      port, vnidRuleStore.get(vnid), Objective.Operation.ADD);
                    parse = null;
                }
            }
        }
    }
}
