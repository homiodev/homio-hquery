package org.homio.hquery.hardware.network;

import lombok.Getter;
import lombok.ToString;
import org.homio.hquery.api.ListParse.BooleanLineParse;
import org.homio.hquery.api.ListParse.LineParse;

@Getter
@ToString
public class Network {

    @BooleanLineParse(value = "IE: WPA Version 1", when = "IE: WPA Version 1", group = 0)
    private boolean encryption_wpa;

    @BooleanLineParse(value = "IE: IEEE 802.11i/WPA2 Version 1", when = "IE: IEEE 802.11i/WPA2 Version 1", group = 0)
    private boolean encryption_wpa2;

    @BooleanLineParse(value = "Encryption key:(on|off)", when = "on")
    private boolean encryption_any;

    @LineParse(".* Signal level=(-??\\d+)[^\\d].*")
    private Integer strength;

    @LineParse("Quality=(\\d+)[^\\d].*")
    private Integer quality;

    @LineParse("Mode:(.*)")
    private String mode;

    @LineParse("Channel:([0-9]{1,2})")
    private Integer channel;

    @LineParse("ESSID:\"(.*)\"")
    private String ssid;

    @LineParse(".* Address: (([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2})")
    private String address;
}
