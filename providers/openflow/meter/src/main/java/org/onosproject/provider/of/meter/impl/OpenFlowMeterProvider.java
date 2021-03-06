/*
 * Copyright 2015 Open Networking Laboratory
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

package org.onosproject.provider.of.meter.impl;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;

import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.net.DeviceId;
import org.onosproject.incubator.net.meter.Meter;
import org.onosproject.incubator.net.meter.MeterFailReason;
import org.onosproject.incubator.net.meter.MeterOperation;
import org.onosproject.incubator.net.meter.MeterOperations;
import org.onosproject.incubator.net.meter.MeterProvider;
import org.onosproject.incubator.net.meter.MeterProviderRegistry;
import org.onosproject.incubator.net.meter.MeterProviderService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.openflow.controller.Dpid;
import org.onosproject.openflow.controller.OpenFlowController;
import org.onosproject.openflow.controller.OpenFlowEventListener;
import org.onosproject.openflow.controller.OpenFlowSwitch;
import org.onosproject.openflow.controller.OpenFlowSwitchListener;
import org.onosproject.openflow.controller.RoleState;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFErrorType;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.errormsg.OFMeterModFailedErrorMsg;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Provider which uses an OpenFlow controller to handle meters.
 */
@Component(immediate = true, enabled = false)
public class OpenFlowMeterProvider extends AbstractProvider implements MeterProvider {


    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OpenFlowController controller;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected MeterProviderRegistry providerRegistry;

    private MeterProviderService providerService;

    private static final AtomicLong XID_COUNTER = new AtomicLong(1);

    static final int POLL_INTERVAL = 10;
    static final long TIMEOUT = 30;

    private Cache<Integer, MeterOperation> pendingOperations;
    private Cache<Long, MeterOperation> pendingXid;


    private InternalMeterListener listener = new InternalMeterListener();
    private Map<Dpid, MeterStatsCollector> collectors = Maps.newHashMap();

    /**
     * Creates a OpenFlow meter provider.
     */
    public OpenFlowMeterProvider() {
        super(new ProviderId("of", "org.onosproject.provider.meter"));
    }

    @Activate
    public void activate() {
        providerService = providerRegistry.register(this);

        pendingOperations = CacheBuilder.newBuilder()
                .expireAfterWrite(TIMEOUT, TimeUnit.SECONDS)
                .removalListener((RemovalNotification<Integer, MeterOperation> notification) -> {
                    if (notification.getCause() == RemovalCause.EXPIRED) {
                        providerService.meterOperationFailed(notification.getValue(),
                                                             MeterFailReason.TIMEOUT);
                    }
                }).build();

        controller.addEventListener(listener);
        controller.addListener(listener);

        controller.getSwitches().forEach((sw -> createStatsCollection(sw)));
    }

    @Deactivate
    public void deactivate() {
        providerRegistry.unregister(this);
        controller.removeEventListener(listener);
        controller.removeListener(listener);
        providerService = null;
    }

    @Override
    public void performMeterOperation(DeviceId deviceId, MeterOperations meterOps) {
        Dpid dpid = Dpid.dpid(deviceId.uri());
        OpenFlowSwitch sw = controller.getSwitch(dpid);
        if (sw == null) {
            log.error("Unknown device {}", deviceId);
            meterOps.operations().forEach(op ->
                                                  providerService.meterOperationFailed(op,
                                                                                       MeterFailReason.UNKNOWN_DEVICE)
            );
            return;
        }

        meterOps.operations().forEach(op -> performOperation(sw, op));
    }

    @Override
    public void performMeterOperation(DeviceId deviceId, MeterOperation meterOp) {
        Dpid dpid = Dpid.dpid(deviceId.uri());
        OpenFlowSwitch sw = controller.getSwitch(dpid);
        if (sw == null) {
            log.error("Unknown device {}", deviceId);
            providerService.meterOperationFailed(meterOp,
                                                 MeterFailReason.UNKNOWN_DEVICE);
            return;
        }

    }

    private void performOperation(OpenFlowSwitch sw, MeterOperation op) {

        pendingOperations.put(op.meter().id().id(), op);


        Meter meter = op.meter();
        MeterModBuilder builder = MeterModBuilder.builder(meter.id().id(), sw.factory());
        if (meter.isBurst()) {
            builder.burst();
        }
        builder.withBands(meter.bands())
                .withId(meter.id())
                .withRateUnit(meter.unit());

        switch (op.type()) {
            case ADD:
                sw.sendMsg(builder.add());
                break;
            case REMOVE:
                sw.sendMsg(builder.remove());
                break;
            case MODIFY:
                sw.sendMsg(builder.modify());
                break;
            default:
                log.warn("Unknown Meter command {}; not sending anything",
                         op.type());
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.UNKNOWN_COMMAND);
        }

    }

    private void createStatsCollection(OpenFlowSwitch sw) {
        if (isMeterSupported(sw)) {
            MeterStatsCollector msc = new MeterStatsCollector(sw, POLL_INTERVAL);
            msc.start();
            collectors.put(new Dpid(sw.getId()), msc);
        }
    }

    private boolean isMeterSupported(OpenFlowSwitch sw) {
        if (sw.factory().getVersion() == OFVersion.OF_10 ||
                sw.factory().getVersion() == OFVersion.OF_11 ||
                sw.factory().getVersion() == OFVersion.OF_12) {
            return false;
        }

        return true;
    }

    private void pushMeterStats(Dpid dpid, OFStatsReply msg) {

    }

    private void signalMeterError(OFMeterModFailedErrorMsg meterError,
                                  MeterOperation op) {
        switch (meterError.getCode()) {
            case UNKNOWN:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.UNKNOWN_DEVICE);
                break;
            case METER_EXISTS:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.EXISTING_METER);
                break;
            case INVALID_METER:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.INVALID_METER);
                break;
            case UNKNOWN_METER:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.UNKNOWN);
                break;
            case BAD_COMMAND:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.UNKNOWN_COMMAND);
                break;
            case BAD_FLAGS:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.UNKNOWN_FLAGS);
                break;
            case BAD_RATE:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.BAD_RATE);
                break;
            case BAD_BURST:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.BAD_BURST);
                break;
            case BAD_BAND:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.BAD_BAND);
                break;
            case BAD_BAND_VALUE:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.BAD_BAND_VALUE);
                break;
            case OUT_OF_METERS:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.OUT_OF_METERS);
                break;
            case OUT_OF_BANDS:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.OUT_OF_BANDS);
                break;
            default:
                providerService.meterOperationFailed(op,
                                                     MeterFailReason.UNKNOWN);
        }
    }

    private class InternalMeterListener
            implements OpenFlowSwitchListener, OpenFlowEventListener {
        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            switch (msg.getType()) {
                case STATS_REPLY:
                    pushMeterStats(dpid, (OFStatsReply) msg);
                    break;
                case ERROR:
                    OFErrorMsg error = (OFErrorMsg) msg;
                    if (error.getErrType() == OFErrorType.METER_MOD_FAILED) {
                        MeterOperation op =
                                pendingOperations.getIfPresent(error.getXid());
                        pendingOperations.invalidate(error.getXid());
                        if (op == null) {
                            log.warn("Unknown Meter operation failed {}", error);
                        } else {
                            OFMeterModFailedErrorMsg meterError =
                                    (OFMeterModFailedErrorMsg) error;
                            signalMeterError(meterError, op);
                        }
                    }
                    break;
                default:
                    break;
            }

        }

        @Override
        public void switchAdded(Dpid dpid) {
            createStatsCollection(controller.getSwitch(dpid));
        }

        @Override
        public void switchRemoved(Dpid dpid) {
            MeterStatsCollector msc = collectors.remove(dpid);
            if (msc != null) {
                msc.stop();
            }
        }

        @Override
        public void switchChanged(Dpid dpid) {

        }

        @Override
        public void portChanged(Dpid dpid, OFPortStatus status) {

        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested, RoleState response) {

        }
    }



}
