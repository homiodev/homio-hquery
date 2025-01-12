package org.homio.hquery.hardware.network;

import static java.lang.String.format;

import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.homio.hquery.api.*;

@HardwareRepository(stringValueOnDisable = "N/A")
public interface NetworkHardwareRepository {

    @SneakyThrows
    static List<NetworkInterface> getActiveNetworkInterfaces() {
        List<NetworkInterface> ifaces = new ArrayList<>();
        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
            NetworkInterface iface = en.nextElement();
            try {
                if (!iface.isUp() || iface.isLoopback() || iface.isPointToPoint()) {
                    continue;
                }
            } catch (final SocketException ex) {
                continue;
            }
            ifaces.add(iface);
        }
        return ifaces;
    }

    static Collection<CidrAddress> getAllInterfaceAddresses() {
        Collection<CidrAddress> interfaceIPs = new ArrayList<>();
        Enumeration<NetworkInterface> en;
        try {
            en = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException ex) {
            return interfaceIPs;
        }

        while (en.hasMoreElements()) {
            NetworkInterface networkInterface = en.nextElement();

            try {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
            } catch (SocketException ignored) {
                continue;
            }

            for (InterfaceAddress cidr : networkInterface.getInterfaceAddresses()) {
                final InetAddress address = cidr.getAddress();
                assert address != null;
                interfaceIPs.add(new CidrAddress(address, cidr.getNetworkPrefixLength()));
            }
        }

        return interfaceIPs;
    }

    @HardwareQuery(name = "Get wifi name", value = "iwgetid -r", printOutput = true)
    String getWifiName();

    @HardwareQuery(name = "Switch hotspot", value = "autohotspot swipe", printOutput = true)
    void switchHotSpot();

    @HardwareQuery(name = "Scan networks", value = "iwlist :iface scan")
    @ErrorsHandler(onRetCodeError = "Got some major errors from our scan command",
            notRecognizeError = "Got some errors from our scan command",
            errorHandlers = {
                    @ErrorsHandler.ErrorHandler(onError = "Device or resource busy",
                            throwError = "Scans are overlapping; slow down putToCache frequency"),
                    @ErrorsHandler.ErrorHandler(onError = "Allocation failed",
                            throwError = "Too many networks for iwlist to handle")
            })
    @ListParse(delimiter = ".*Cell \\d\\d.*", clazz = Network.class)
    List<Network> scan(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Network stat", value = "iwconfig :iface")
    @ErrorsHandler(onRetCodeError = "Error getting wireless devices information")
    NetworkStat stat(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Disable network", value = "ifconfig :iface down")
    @ErrorsHandler(onRetCodeError = "There was an unknown error disabling the interface",
            notRecognizeError = "There was an error disabling the interface")
    void disable(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Restart network interface", value = "wpa_cli -i :iface reconfigure", printOutput = true)
    void restartNetworkInterface(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Enable network", value = "ifconfig :iface up")
    @ErrorsHandler(onRetCodeError = "There was an unknown error enabling the interface",
            notRecognizeError = "There was an error enabling the interface",
            errorHandlers = {
                    @ErrorsHandler.ErrorHandler(onError = "No such device", throwError = "The interface :iface does not exist."),
                    @ErrorsHandler.ErrorHandler(onError = "Allocation failed",
                            throwError = "Too many networks for iwlist to handle")
            })
    void enable(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Connect wep", value = "iwconfig :iface essid ':essid' key :PASSWORD")
    void connect_wep(@HQueryParam("iface") String iface, @HQueryParam("essid") String essid,
                     @HQueryParam("password") String password);

    @ErrorsHandler(onRetCodeError = "Shit is broken TODO")
    @HardwareQuery(name = "Connect wpa",
            value = "wpa_passphrase ':essid' ':password' > wpa-temp.conf && wpa_supplicant -D wext -i :iface -c wpa-temp.conf " +
                    "&& rm wpa-temp.conf")
    void connect_wpa(@HQueryParam("iface") String iface, @HQueryParam("essid") String essid,
                     @HQueryParam("password") String password);

    @HardwareQuery(name = "Connect open", value = "iwconfig :iface essid ':essid'")
    void connect_open(@HQueryParam("iface") String iface, @HQueryParam("essid") String essid);

    @HardwareQuery(name = "Get network description", value = "ifconfig :iface", ignoreOnError = true)
    NetworkDescription getNetworkDescription(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Get wifi password",
            value = "grep -r 'psk=' /etc/wpa_supplicant/wpa_supplicant.conf | cut -d = -f 2 | cut -d \\\" -f 2")
    String getWifiPassword();

    @CurlQuery(value = "http://checkip.amazonaws.com", cache = true, ignoreOnError = true,
            mapping = TrimEndMapping.class, valueOnError = "127.0.0.1")
    String getOuterIpAddress();

    default Map<String, Callable<Integer>> buildPingIpAddressTasks(String pinIpAddressRange, Consumer<String> log, Set<Integer> ports,
                                                                   int timeout, BiConsumer<String, Integer> handler) {
        if (pinIpAddressRange == null) {
            throw new IllegalArgumentException(
                    "Unable to proceed due ip address not found. Please check you connected to Router");
        }
        if (!Pattern.compile(NetworkDescription.IP_RANGE_PATTERN).matcher(pinIpAddressRange).matches()) {
            throw new IllegalArgumentException("Address not match patter xxx.xxx.xxx-xxx");
        }
        Map<String, Callable<Integer>> tasks = new HashMap<>();
        String[] parts = pinIpAddressRange.split("-");
        String[] ipParts = parts[0].split("\\.");
        String ipPrefix = parts[0].substring(0, parts[0].lastIndexOf(".") + 1);

        for (Integer port : ports) {
            for (int i = Integer.parseInt(ipParts[3]); i < Integer.parseInt(parts[1]); i++) {
                int ipSuffix = i;
                tasks.put("check-ip-" + ipSuffix + "-port-" + port, () -> {
                    String ipAddress = ipPrefix + ipSuffix;
                    log.accept(format("Check ip: %s:%s", ipAddress, port));
                    if (pingAddress(ipAddress, port, timeout)) {
                        handler.accept(ipAddress, port);
                        return ipSuffix;
                    }
                    return null;
                });
            }
        }
        return tasks;
    }

    default boolean pingAddress(String ipAddress, int port, int timeout) {
        try {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ipAddress, port), timeout);
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    @SneakyThrows
    default String getIPAddress() {
        String address = null;
        try {
            try (final DatagramSocket socket = new DatagramSocket()) {
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                address = socket.getLocalAddress().getHostAddress();
            }
        } catch (Exception ignore) {
        }
        if (StringUtils.isEmpty(address) || address.equals("0.0.0.0")) {
            if (!SystemUtils.IS_OS_WINDOWS) {
                String inet = getNetworkDescription().map(NetworkDescription::getInet).orElse(null);
                if (StringUtils.isNotEmpty(inet) && !"127.0.0.1".equals(inet)) {
                    address = inet;
                }
            } else {
                try {
                    for (Enumeration<NetworkInterface> enumNetworks = NetworkInterface.getNetworkInterfaces(); enumNetworks
                            .hasMoreElements(); ) {
                        NetworkInterface networkInterface = enumNetworks.nextElement();
                        for (Enumeration<InetAddress> enumIpAddr = networkInterface.getInetAddresses(); enumIpAddr
                                .hasMoreElements(); ) {
                            InetAddress inetAddress = enumIpAddr.nextElement();
                            if (!inetAddress.isLoopbackAddress() && inetAddress.getHostAddress().length() < 18
                                && inetAddress.isSiteLocalAddress()) {
                                address = inetAddress.getHostAddress();
                                break;
                            }
                        }
                    }
                } catch (SocketException ignored) {
                }
            }
        }

        if (StringUtils.isEmpty(address)) {
            address = InetAddress.getLocalHost().getHostAddress();
        }

        return address;
    }

    @SneakyThrows
    default String getMacAddress() {
        if (SystemUtils.IS_OS_LINUX) {
            return getNetworkDescription().map(NetworkDescription::getMac).orElse(null);
        } else {
            InetAddress localHost = InetAddress.getLocalHost();
            NetworkInterface ni = NetworkInterface.getByInetAddress(localHost);
            byte[] hardwareAddress = ni.getHardwareAddress();

            String[] hexadecimal = new String[hardwareAddress.length];
            for (int i = 0; i < hardwareAddress.length; i++) {
                hexadecimal[i] = format("%02X", hardwareAddress[i]);
            }
            return String.join("-", hexadecimal);
        }
    }

    @SneakyThrows
    default void setWifiCredentials(String ssid, String password, String country) {
        String code = """
                ctrl_interface=DIR=/var/run/wpa_supplicant GROUP=netdev
                update_config=1
                country=%s
                            
                network={
                     ssid="%s"
                     psk="%s"
                     scan_ssid=1
                }
                """.formatted(country, ssid, password);
        Files.write(Paths.get("/etc/wpa_supplicant/wpa_supplicant.conf"), code.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    @HardwareQuery(name = "Get active network interface", value = "ip addr | awk '/state UP/ {print $2}' | sed 's/.$//'")
    String getActiveNetworkInterface();

    @HardwareQuery(name = "Set wifi power save off", value = "iw :iface set power_save off")
    void setWifiPowerSaveOff(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Check ssh keys exists", value = "test -f ~/.ssh/id_rsa", cacheValid = 3600)
    boolean isSshGenerated();

    @HardwareQuery(name = "Test if cable ethX connected", value = "cat /sys/class/net/:iface/carrier")
    boolean isNetworkCableConnected(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Generate ssh keys", value = "cat /dev/zero | ssh-keygen -q -N \"\"")
    void generateSSHKeys();

    default Optional<NetworkDescription> getNetworkDescription() {
        if (!SystemUtils.IS_OS_WINDOWS) {
            String activeNetworkInterface = getActiveNetworkInterface();
            if (StringUtils.isNotEmpty(activeNetworkInterface)) {
                return Optional.ofNullable(getNetworkDescription(activeNetworkInterface));
            }
        }
        return Optional.empty();
    }

    class TrimEndMapping implements Function<Object, Object> {

        @Override
        public Object apply(Object o) {
            return ((String) o).trim().replaceAll("\n", "");
        }
    }

    record CidrAddress(InetAddress address, short prefix) {
    }
}
