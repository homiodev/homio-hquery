package org.homio.hquery.hardware.other;

import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.homio.hquery.api.ListParse.LineParse;

@Getter
@ToString
public class HardwareOs {

    @LineParse("ID=(.*)")
    private String id;

    @LineParse("ID_LIKE=(.*)")
    private String idLike;

    @LineParse("NAME=(.*)")
    private String name;

    @LineParse("VERSION=(.*)")
    private String version;

    public String getPackageManager() {
        switch (StringUtils.defaultString(idLike, id)) {
            case "debian":
            case "ubuntu":
                return "apt";
            case "rhel fedora":
                return "yum";
        }
        throw new IllegalStateException("Unable to find package manager");
    }
}
