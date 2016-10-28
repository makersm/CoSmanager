package org.iris4sdn.csdncm.cosmanager.impl;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.flowobjective.Objective;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;


/**
 * This is CoSRulleInstaller that controls Rule about ovs queue.
 */
public class CoSRuleInstaller {
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private final Logger log = getLogger(getClass());

    private final FlowObjectiveService flowObjectiveService;
    private final DeviceService deviceService;

    private final ApplicationId appId;
    private static final int DEFAULT_PRIORITY = 25000;
    private static final int DEFAULT_TIMEOUT = 10;

    private CoSRuleInstaller(ApplicationId appId) {
        ServiceDirectory serviceDirectory = new DefaultServiceDirectory();
        this.flowObjectiveService = serviceDirectory.get(FlowObjectiveService.class);
        this.deviceService = serviceDirectory.get(DeviceService.class);
        this.appId = appId;
    }

    public static CoSRuleInstaller ruleInstaller(ApplicationId appId) {
        return new CoSRuleInstaller(appId);
    }

    public void programCoSIn(PortNumber inPort, MacAddress srcMac, MacAddress dstMac,
                             byte ipProtocolType, short etherProtocolType, IpPrefix srcIp,
                             IpPrefix dstIp, TpPort srcPort, TpPort dstPort, PortNumber outPort,
                             long queueNum, DeviceId deviceId, Objective.Operation type) {

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchInPort(inPort)
                .matchEthSrc(srcMac)
                .matchEthDst(dstMac)
                .matchIPProtocol(ipProtocolType)
                .matchEthType(etherProtocolType)
                .matchIPSrc(srcIp)
                .matchIPDst(dstIp)
                .matchUdpSrc(srcPort)
                .matchUdpDst(dstPort)
                .build();

        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        treatment.setQueue(queueNum, outPort);

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build()).withSelector(selector)
                .withFlag(ForwardingObjective.Flag.VERSATILE).makeTemporary(DEFAULT_TIMEOUT)
                .fromApp(appId).withPriority(DEFAULT_PRIORITY);

            forward(deviceId, objective, type);
    }

    private void forward(DeviceId deviceId, ForwardingObjective.Builder objective, Objective.Operation type) {
        if (type.equals(Objective.Operation.ADD)) {
            log.info("operation: {}, id: {}", type, deviceId);
            flowObjectiveService.forward(deviceId, objective.add());
        } else {
            log.info("operation: {}, id: {}", type, deviceId);
            flowObjectiveService.forward(deviceId, objective.remove());
        }
    }
}




