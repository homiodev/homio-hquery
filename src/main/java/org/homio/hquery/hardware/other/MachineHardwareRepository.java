package org.homio.hquery.hardware.other;

import com.sun.management.OperatingSystemMXBean;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.api.HQueryMaxWaitTimeout;
import org.homio.hquery.api.HQueryParam;
import org.homio.hquery.api.HardwareQuery;
import org.homio.hquery.api.HardwareRepository;

/**
 * ProgressBar may be null
 */
@HardwareRepository(stringValueOnDisable = "N/A")
public interface MachineHardwareRepository {

    AtomicReference<MachineInfo> MACHINE_INFO = new AtomicReference<>();
    OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") String command);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true,
                   ignoreOnError = true, redirectErrorsToInputs = true)
    String executeNoErrorThrow(@HQueryParam("command") String command, @HQueryMaxWaitTimeout int maxSecondsTimeout,
        ProgressBar progressBar);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true,
                   ignoreOnError = true, redirectErrorsToInputs = true)
    ArrayList<String> executeNoErrorThrowList(@HQueryParam("command") String command,
        @HQueryMaxWaitTimeout int maxSecondsTimeout,
        ProgressBar progressBar);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") String command, ProgressBar progressBar);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") String command, @HQueryMaxWaitTimeout int maxSecondsTimeout);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") String command, @HQueryMaxWaitTimeout int maxSecondsTimeout,
        ProgressBar progressBar);

    @HardwareQuery(name = "Get cpu temperature", value = "vcgencmd measure_temp | grep  -o -E '[[:digit:]].*'",
                   printOutput = true)
    String getCpuTemperature();

    @HardwareQuery(name = "Get service status", value = "systemctl is-active :serviceName", printOutput = true)
    int getServiceStatus(@HQueryParam("serviceName") String serviceName);

    @HardwareQuery(name = "Reboot device", value = "reboot", printOutput = true)
    void reboot();

    @HardwareQuery(name = "Get OS name", value = "cat /etc/os-release", cacheValid = Integer.MAX_VALUE, printOutput = true)
    HardwareOs getOs();
    @HardwareQuery(name = "Change file permission", value = "chmod :mode -R :path", printOutput = true)
    void setPermissions(@HQueryParam("path") Path path, @HQueryParam("mode") int mode);

    @HardwareQuery(name = "Install software", value = "$PM install -y :soft", printOutput = true)
    void installSoftware(@HQueryParam("soft") String soft, @HQueryMaxWaitTimeout int maxSecondsTimeout);

    @HardwareQuery(name = "Install software", value = "$PM install -y :soft", printOutput = true)
    void installSoftware(@HQueryParam("soft") String soft, @HQueryMaxWaitTimeout int maxSecondsTimeout,
        ProgressBar progressBar);

    @HardwareQuery(name = "Update", value = "$PM update -y && $PM full-upgrade -y && $PM autoremove -y && $PM clean -y && $PM autoclean -y", printOutput = true)
    void update(@HQueryMaxWaitTimeout int maxSecondsTimeout, ProgressBar progressBar);

    @HardwareQuery(name = "Enable systemctl service", value = "systemctl enable :soft", printOutput = true)
    void enableSystemCtl(@HQueryParam("soft") String soft);

    @HardwareQuery(name = "Start systemctl service", value = "systemctl start :soft", printOutput = true)
    void startSystemCtl(@HQueryParam("soft") String soft);

    @HardwareQuery(name = "Stop systemctl service", value = "systemctl stop :soft", printOutput = true)
    void stopSystemCtl(@HQueryParam("soft") String soft);

    @HardwareQuery(name = "Check software installed", value = "which :soft", win = "where :soft", cacheValid = 60)
    boolean isSoftwareInstalled(@HQueryParam("soft") String soft);

    default void enableAndStartSystemctl(String soft) {
        enableSystemCtl(soft);
        startSystemCtl(soft);
    }

    @SneakyThrows
    // format(used/total) 200/900mb
    default String getDiscCapacity() {
        File[] roots = File.listRoots();
        List<String> disks = new ArrayList<>();
        for (File root : roots) {
            String path = root.getAbsolutePath();
            long totalSpace = root.getTotalSpace();
            DiscCapacity discCapacity = getFormat(totalSpace);
            String used = formatCapacity(totalSpace - root.getUsableSpace(), discCapacity);
            String total = formatCapacity(totalSpace, discCapacity);
            String content = String.format("%s/%s%s", used, total, (discCapacity.name() + (discCapacity == DiscCapacity.B ? "" : "B")));
            if (roots.length == 1) {
                return content;
            }
            disks.add("%s:%s".formatted(path, content));
        }
        return String.join("; ", disks);
    }

    default String getCpuLoad() {
        return osBean.getCpuLoad() * 100F + "%";
    }


    // format(used/total) 200/900mb
    default String getRamMemory() {
        long totalMemory = osBean.getTotalMemorySize();
        long usedMemory = totalMemory - osBean.getFreeMemorySize();
        return usedMemory / (1024 * 1024) + "/" + totalMemory / (1024 * 1024) + "mb";
    }

    default String getUptime() {
        long uptimeMillis = System.nanoTime() / 1_000_000;
        long uptimeMinutes = (uptimeMillis / (1000 * 60)) % 60;
        long uptimeHours = (uptimeMillis / (1000 * 60 * 60)) % 24;
        long uptimeDays = uptimeMillis / (1000 * 60 * 60 * 24);

        if (uptimeDays > 0) {
            return uptimeDays + "d, " + uptimeHours + "hr, " + uptimeMinutes + "min";
        } else if (uptimeHours > 0) {
            return uptimeHours + "hr, " + uptimeMinutes + "min";
        }
        return uptimeMinutes + "min";
    }

    default MachineInfo getMachineInfo() {
        if (MACHINE_INFO.get() == null) {
            MachineInfo info = new MachineInfo();
            info.cpuNum = Runtime.getRuntime().availableProcessors();
            info.os = SystemUtils.OS_NAME + ". Version: " + SystemUtils.OS_VERSION + ". Arch: " + SystemUtils.OS_ARCH;
            if (SystemUtils.IS_OS_LINUX) {
                info.networkNodeHostname = execute("hostname");
                info.kernelName = execute("uname -s");
                info.kernelRelease = execute("uname -r");
                info.kernelVersion = execute("uname -v");
                info.machineName = execute("uname -m");
                info.processorType = execute("uname -p");
                info.operationSystem = execute("uname -o");

                ArrayList<String> list = executeNoErrorThrowList("lscpu", 60, null);
                info.architecture = parseInfoLine(list, "Architecture");
                info.cpuVendorID = parseInfoLine(list, "Vendor ID");
                info.processorModelName = parseInfoLine(list, "Model name");
            } else {
                info.networkNodeHostname = execute("hostname");
            }
            MACHINE_INFO.set(info);
        }
        return MACHINE_INFO.get();
    }

    private static String parseInfoLine(ArrayList<String> list, String Architecture) {
        return list.stream().filter(l -> l.contains(Architecture)).map(l -> StringUtils.trimToNull(l.split(":")[1])).findAny().orElse(null);
    }

    private static String formatCapacity(long bytes, DiscCapacity discCapacity) {
        DecimalFormat df = new DecimalFormat("#.##");
        int unit = 1024;
        return df.format(bytes / Math.pow(unit, discCapacity.ordinal()));
    }

    private static DiscCapacity getFormat(long bytes) {
        if (bytes < 1024) {
            return DiscCapacity.B;
        }
        int unit = 1024;
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char[] pre = {'B', 'K', 'M', 'G', 'T', 'P', 'E'};
        return DiscCapacity.valueOf(String.valueOf(pre[exp]));
    }

    enum DiscCapacity {
        B, K, M, G, T, P, E
    }
}
