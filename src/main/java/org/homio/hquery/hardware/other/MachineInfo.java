package org.homio.hquery.hardware.other;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class MachineInfo {

    public String kernelName;
    public String networkNodeHostname;
    public String kernelRelease;
    public String kernelVersion;
    public String machineName;
    public String processorType;
    public String operationSystem;
    public String processorModelName;
    public String architecture;
    public int cpuNum;
    public String cpuVendorID;
    public String os;
}
