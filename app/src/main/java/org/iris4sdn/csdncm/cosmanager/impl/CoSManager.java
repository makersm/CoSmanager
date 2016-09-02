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
import org.iris4sdn.csdncm.vxlanflowmapper.OuterPacket;
import org.iris4sdn.csdncm.vxlanflowmapper.VxlanFlowMappingService;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onlab.packet.UDP;
import org.onlab.util.KryoNamespace;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
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

    private CoSListener vnidRuleListener;
    private CoSPacketProcessor processor;
    private static CoSRuleInstaller installer;

    @Activate
    public void activate() {

        vnidRuleListener = new CoSListener();
        processor = new CoSPacketProcessor();

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

        log.info("-------------!{} started!--------------", appId.id());
    }

    @Deactivate
    public void deactivate() {
        vnidRuleStore.removeListener(vnidRuleListener);
        packetService.removeProcessor(processor);

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
            if (id.mac().isLinkLocal()) {
                return null;
            }

            Host dst = hostService.getHost(id);
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
    }

    private void processCoSchecker(PacketContext context, Ethernet ethernet) {
        //FIXME Is parsing outerPacket in cos manager right?
        MacAddress outerSrcMac = ethernet.getSourceMAC();
        MacAddress outerDstMac = ethernet.getDestinationMAC();

        IPv4 outerIpv4Packet = (IPv4)ethernet.getPayload();
        Ip4Address outerSrcIp = Ip4Address.valueOf(outerIpv4Packet.getSourceAddress());
        Ip4Address outerDstIp = Ip4Address.valueOf(outerIpv4Packet.getDestinationAddress());

        UDP udpPacket = (UDP)outerIpv4Packet.getPayload();
        int outerSrcPort = udpPacket.getSourcePort();
        int outerDstPort = udpPacket.getDestinationPort();

        OuterPacket outerPacket = new DefaultOuterPacket(
                outerSrcMac, outerDstMac, outerSrcIp,
                outerDstIp, outerSrcPort, outerDstPort
        );

        log.info("outerPacket : {}", outerPacket.toString());

        InnerPacket innerPacket = vxlanFlowMappingService.getInnerPacket(outerPacket);
        if(innerPacket == null) {
            return;
        }

        PortNumber outPort = getPortNumPath(context);
        PortNumber inPort = context.inPacket().receivedFrom().port();
        DeviceId deviceId = context.inPacket().receivedFrom().deviceId();
        Integer vnid = innerPacket.vnid();
        log.info("CoSPacketProcessor : {}", vnid);

        if(outPort == null || !vnidRuleStore.containsKey(vnid)) {
            log.info("no proper port or CoS");
            return;
        }

        installer.programCoSIn(
                inPort, outerSrcMac, outerDstMac, IPv4.PROTOCOL_UDP, Ethernet.TYPE_IPV4,
                IpPrefix.valueOf(outerSrcIp, IpPrefix.MAX_INET_MASK_LENGTH),
                IpPrefix.valueOf(outerDstIp, IpPrefix.MAX_INET_MASK_LENGTH),
                TpPort.tpPort(outerSrcPort), TpPort.tpPort(outerDstPort),
                outPort, vnidRuleStore.get(vnid),
                deviceId, Objective.Operation.ADD
        );
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
                    processCoSchecker(context, ethernet);
                }
            }
        }
    }
}
