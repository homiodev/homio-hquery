package org.homio.hquery.hardware.other;

import java.nio.file.Path;
import java.util.ArrayList;
import org.apache.commons.lang3.SystemUtils;
import org.homio.hquery.HQueryProgressBar;
import org.homio.hquery.api.HQueryMaxWaitTimeout;
import org.homio.hquery.api.HQueryParam;
import org.homio.hquery.api.HardwareQuery;
import org.homio.hquery.api.HardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@HardwareRepository(stringValueOnDisable = "N/A")
public interface MachineHardwareRepository {

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") @NotNull String command);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true,
                   ignoreOnError = true, redirectErrorsToInputs = true)
    String executeNoErrorThrow(@HQueryParam("command") @NotNull String command, @HQueryMaxWaitTimeout int maxSecondsTimeout,
        @Nullable HQueryProgressBar progressBar);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true,
                   ignoreOnError = true, redirectErrorsToInputs = true)
    ArrayList<String> executeNoErrorThrowList(@HQueryParam("command") @NotNull String command,
        @HQueryMaxWaitTimeout int maxSecondsTimeout,
        @Nullable HQueryProgressBar progressBar);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") @NotNull String command, @Nullable HQueryProgressBar progressBar);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") @NotNull String command, @HQueryMaxWaitTimeout int maxSecondsTimeout);

    @HardwareQuery(name = "Execute general command", value = ":command", win = ":command", printOutput = true)
    String execute(@HQueryParam("command") @NotNull String command, @HQueryMaxWaitTimeout int maxSecondsTimeout,
        @Nullable HQueryProgressBar progressBar);

    @HardwareQuery(name = "Get Disc space", value = "df -k /", printOutput = true)
    HardwareMemory getSDCardMemory();

    @HardwareQuery(name = "Get cpu load", value = "top -bn1 | grep load | awk '{printf \"%.2f%%\", $(NF-2)}'", printOutput = true)
    String getCpuLoad();

    @HardwareQuery(name = "Get cpu temperature", value = "vcgencmd measure_temp | grep  -o -E '[[:digit:]].*'",
                   printOutput = true)
    String getCpuTemperature();

    @HardwareQuery(name = "Get memory", value = "free -m | awk 'NR==2{printf \"%s/%sMB\", $3,$2 }'", printOutput = true)
    String getMemory();

    @HardwareQuery(name = "Get uptime", value = "uptime -p | cut -d 'p' -f 2 | awk '{ printf \"%s\", $0 }'", printOutput = true)
    String getUptime();

    @HardwareQuery(name = "Get device model", value = "cat /proc/device-tree/model", cacheValid = Integer.MAX_VALUE)
    String catDeviceModel();

    @HardwareQuery(name = "Get hostname", value = "hostname --short", win = "hostname", printOutput = true)
    String getHostname();

    @HardwareQuery(name = "Get service status", value = "systemctl is-active :serviceName", printOutput = true)
    int getServiceStatus(@HQueryParam("serviceName") String serviceName);

    @HardwareQuery(name = "Reboot device", value = "reboot", printOutput = true)
    void reboot();

    @HardwareQuery(name = "Get OS name", value = "cat /etc/os-release", cacheValid = Integer.MAX_VALUE, printOutput = true)
    HardwareOs getOs();

    @HardwareQuery(name = "Change file permission", value = "chmod :mode -R :path", printOutput = true)
    void setPermissions(@HQueryParam("path") Path path, @HQueryParam("mode") int mode);

    @HardwareQuery(name = "Install software", value = "$PM install -y :soft", printOutput = true)
    void installSoftware(@HQueryParam("soft") @NotNull String soft, @HQueryMaxWaitTimeout int maxSecondsTimeout);

    @HardwareQuery(name = "Install software", value = "$PM install -y :soft", printOutput = true)
    void installSoftware(@HQueryParam("soft") @NotNull String soft, @HQueryMaxWaitTimeout int maxSecondsTimeout,
        @Nullable HQueryProgressBar progressBar);

    @HardwareQuery(name = "Enable systemctl service", value = "systemctl enable :soft", printOutput = true)
    void enableSystemCtl(@HQueryParam("soft") @NotNull String soft);

    @HardwareQuery(name = "Start systemctl service", value = "systemctl start :soft", printOutput = true)
    void startSystemCtl(@HQueryParam("soft") @NotNull String soft);

    @HardwareQuery(name = "Stop systemctl service", value = "systemctl stop :soft", printOutput = true)
    void stopSystemCtl(@HQueryParam("soft") @NotNull String soft);

    default void enableAndStartSystemctl(@NotNull String soft) {
        enableSystemCtl(soft);
        startSystemCtl(soft);
    }

    @HardwareQuery(name = "Check software installed", value = "which :soft", win = "where :soft", cacheValid = 60)
    boolean isSoftwareInstalled(@HQueryParam("soft") @NotNull String soft);

    default String getDeviceModel() {
        return SystemUtils.IS_OS_WINDOWS ? SystemUtils.OS_NAME : catDeviceModel();
    }
}
