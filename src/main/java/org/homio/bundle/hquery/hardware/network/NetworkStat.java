package org.homio.bundle.hquery.hardware.network;

import lombok.Getter;
import lombok.ToString;
import org.homio.bundle.hquery.api.ListParse.LineParse;

@Getter
@ToString
public class NetworkStat {

    @LineParse("Mode:([^\\s]+).*")
    private String mode;

    @LineParse(".*Frequency:(.*) GHz.*")
    private String frequency;

    @LineParse("Bit Rate=(\\d+) Mb/s.*")
    private String bitRate;

    @LineParse(".* ESSID:\"(.*)\"")
    private String ssid;

    @LineParse(".* Access Point: ([a-fA-F0-9:]*)")
    private String accessPoint;

    @LineParse(".* Signal level=(-??\\d+)[^\\d].*")
    private Integer strength;

    @LineParse(".* Quality=(\\d+)[^\\d].*")
    private Integer quality;
}
