package org.homio.hquery.hardware.other;

import lombok.Getter;
import lombok.ToString;
import org.homio.hquery.api.ListParse.LineParse;

@Getter
@ToString
public class HardwareOs {

    @LineParse("ID=(?:\"(\\w+)\")?")
    private String id;

    @LineParse("ID_LIKE=(?:\"(\\w+)\")?")
    private String idLike;

    @LineParse("NAME=(?:\"(\\w+)\")?")
    private String name;

    @LineParse("VERSION=(?:\"(\\w+)\")?")
    private String version;

    public String getPackageManager() {
        if (idLike != null) {
            switch (idLike) {
                case "debian", "ubuntu" -> {
                    return "apt";
                }
                case "rhel fedora", "fedora", "centos" -> {
                    return "dnf";
                }
            }
        }
        switch (id) {
            case "debian", "ubuntu" -> {
                return "apt";
            }
            case "fedora", "centos" -> {
                return "dnf";
            }
        }
        throw new IllegalStateException("Unable to find package manager");
    }
}
