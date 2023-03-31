package org.homio.bundle.hquery.hardware.network;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Logger;
import org.homio.bundle.hquery.api.CurlQuery;
import org.homio.bundle.hquery.api.ErrorsHandler;
import org.homio.bundle.hquery.api.HQueryParam;
import org.homio.bundle.hquery.api.HardwareQuery;
import org.homio.bundle.hquery.api.HardwareRepository;
import org.homio.bundle.hquery.api.ListParse;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@HardwareRepository(stringValueOnDisable = "N/A")
public interface NetworkHardwareRepository {

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

    @CurlQuery(value = "http://checkip.amazonaws.com", cacheValid = 3600, ignoreOnError = true,
               mapping = TrimEndMapping.class, valueOnError = "127.0.0.1")
    String getOuterIpAddress();

    @CurlQuery(value = "http://ip-api.com/json/:ip", cache = true, ignoreOnError = true)
    IpGeoLocation getIpGeoLocation(@HQueryParam("ip") String ip);

    @CurlQuery(value = "https://geocode.xyz/:city?json=1", cache = true, ignoreOnError = true)
    CityToGeoLocation findCityGeolocation(@HQueryParam("city") String city);

    default CityToGeoLocation findCityGeolocationOrThrowException(String city) {
        CityToGeoLocation cityGeolocation = findCityGeolocation(city);
        if (cityGeolocation.error != null) {
            String error = cityGeolocation.error.description;
            if ("15. Your request did not produce any results.".equals(error)) {
                error = "Unable to find city: " + city + ". Please, check city from site: https://geocode.xyz";
            }
            throw new IllegalArgumentException(error);
        }
        return cityGeolocation;
    }

    default Map<String, Callable<Integer>> buildPingIpAddressTasks(String pinIpAddressRange, Logger log, Set<Integer> ports,
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
                    log.debug("Check ip: {}:{}", ipAddress, port);
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
        if (SystemUtils.IS_OS_WINDOWS) {
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
                        }
                    }
                }
            } catch (SocketException ignored) {
            }
        } else {
            String inet = getNetworkDescription().map(NetworkDescription::getInet).orElse(null);
            if (StringUtils.isNotEmpty(inet) && !"127.0.0.1".equals(inet)) {
                address = inet;
            }
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
                hexadecimal[i] = String.format("%02X", hardwareAddress[i]);
            }
            return String.join("-", hexadecimal);
        }
    }

    @SneakyThrows
    default void setWifiCredentials(String ssid, String password, String country) {
        TemplateEngine templateEngine = new TemplateEngine();
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.HTML);
        templateEngine.setTemplateResolver(templateResolver);

        Context context = new Context();
        context.setVariable("SSID", ssid);
        context.setVariable("PASSWORD", password);
        context.setVariable("COUNTRY", country);

        StringWriter stringWriter = new StringWriter();
        templateEngine.process("templates/wpa_supplicant.conf", context, stringWriter);
        String value = stringWriter.toString();

        Files.write(Paths.get("/etc/wpa_supplicant/wpa_supplicant.conf"), value.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
    }

    @HardwareQuery(name = "Get active network interface", value = "ip addr | awk '/state UP/ {print $2}' | sed 's/.$//'")
    String getActiveNetworkInterface();

    @HardwareQuery(name = "Set wifi power save off", value = "iw :iface set power_save off")
    void setWifiPowerSaveOff(@HQueryParam("iface") String iface);

    @HardwareQuery(name = "Check ssh keys exists", value = "test -f ~/.ssh/id_rsa", cacheValid = 3600)
    boolean isSshGenerated();

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

    @Getter
    class CityToGeoLocation {

        private String longt;
        private String latt;
        private Error error;

        @Setter
        private static class Error {

            private String description;
        }
    }

    @Getter
    class IpGeoLocation {

        private final String country = "unknown";
        private final String countryCode = "unknown";
        private final String region = "unknown";
        private final String regionName = "unknown";
        private final String city = "unknown";
        private final Integer lat = 0;
        private final Integer lon = 0;
        private final String timezone = "unknown";

        @Override
        @SneakyThrows
        public String toString() {
            return new ObjectMapper().writeValueAsString(this);
        }
    }

    class TrimEndMapping implements Function<Object, Object> {

        @Override
        public Object apply(Object o) {
            return ((String) o).trim().replaceAll("\n", "");
        }
    }
}
