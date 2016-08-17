package org.iris4sdn.csdncm.cosmanager.impl;

import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.osgi.DefaultServiceDirectory;
import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.TpPort;
import org.onosproject.cli.Comparators;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.Device;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
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
    private static final int DEFAULT_PRIORITY = 50000;

    private CoSRuleInstaller(ApplicationId appId) {
        ServiceDirectory serviceDirectory = new DefaultServiceDirectory();
        this.flowObjectiveService = serviceDirectory.get(FlowObjectiveService.class);
        this.deviceService = serviceDirectory.get(DeviceService.class);
        this.appId = appId;
    }

    public static CoSRuleInstaller ruleInstaller(ApplicationId appId) {
        return new CoSRuleInstaller(appId);
    }

    private static List<Device> getSortedDevices(DeviceService service) {
        List<Device> devices = newArrayList(service.getDevices());
        Collections.sort(devices, Comparators.ELEMENT_COMPARATOR);
        return devices;
    }

    private List<Device> getAvailableSwitch(List<Device> devices) {
        List<Device> new_device = newArrayList();
        for (Device device : devices) {
            //TODO find nec switch; we found buffalo by swVersion
            if (deviceService.isAvailable(device.id()) && device.type() == Device.Type.SWITCH && device.swVersion().equals("2.4.0")) {
                new_device.add(device);
            }
        }
        return new_device;
    }

    //TODO generate selector
    public void enqueue(Map<String, String> decodedPacket, long num, Objective.Operation type) {
        String srcMacIndex = "outerSrcMac";
        String dstMacIndex = "outerDstMac";
        String srcIpIndex = "outerSrcIp";
        String dstIpIndex = "outerDstIp";
        String srcPortIndex = "outerSrcPort";
        log.info("enqueue; decodedPacket : {}", decodedPacket.toString());

        //FIXME IP selector prob
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthSrc(MacAddress.valueOf(decodedPacket.get(srcMacIndex)))
                .matchEthDst(MacAddress.valueOf(decodedPacket.get(dstMacIndex)))
                .matchIPProtocol(IPv4.PROTOCOL_UDP)
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPSrc(IpPrefix.valueOf(IpAddress.valueOf(decodedPacket.get(srcIpIndex)), IpPrefix.MAX_INET_MASK_LENGTH))
                .matchIPDst(IpPrefix.valueOf(IpAddress.valueOf(decodedPacket.get(dstIpIndex)), IpPrefix.MAX_INET_MASK_LENGTH))
                .matchUdpSrc(TpPort.tpPort(Integer.parseInt(decodedPacket.get(srcPortIndex))))
                .build();


        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder();
        treatment.setQueue(num);

        ForwardingObjective.Builder objective = DefaultForwardingObjective
                .builder().withTreatment(treatment.build()).withSelector(selector)
                .withFlag(ForwardingObjective.Flag.VERSATILE).makeTemporary(10)
                .fromApp(appId).withPriority(DEFAULT_PRIORITY);

        for (Device device : getAvailableSwitch(getSortedDevices(deviceService))) {
            log.info("device id : {}, device type : {}, device availablity : {}",
                     device.id(), device.type(), deviceService.isAvailable(device.id()));
            forward(device, objective, type);
        }
    }

    private void forward(Device device, ForwardingObjective.Builder objective, Objective.Operation type) {
            if(type.equals(Objective.Operation.ADD)) {
                log.info("operation: {}, id: {}",type, device.id());
                flowObjectiveService.forward(device.id(), objective.add());
            }
            else {
                log.info("operation: {}, id: {}",type, device.id());
                flowObjectiveService.forward(device.id(), objective.remove());
            }
//        Method method = null;
//        try {
//            method = objective.getClass().getDeclaredMethod(type.toString().toLowerCase());
//            assert method != null;
//            for (Device device : devices)
//                flowObjectiveService.forward(device.id(), (ForwardingObjective) method.invoke(objective));
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
//        }
    }
}



