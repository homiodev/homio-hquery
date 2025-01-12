package org.homio.hquery.hardware.other;

import lombok.Getter;
import lombok.ToString;
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

  @LineParse("PRETTY_NAME=(.*)")
  private String prettyName;

  @LineParse("VERSION=(.*)")
  private String version;

  @LineParse("VERSION_CODENAME=(.*)")
  private String versionCodename;

  public String getPackageManager() {
    if (idLike != null) {
      switch (idLike) {
        case "debian", "ubuntu" -> {
          return "apt";
        }
        case "alpine" -> {
          return "apk";
        }
        case "rhel fedora", "fedora", "centos" -> {
          return "dnf";
        }
      }
    }
    switch (id) {
      case "alpine" -> {
        return "apk";
      }
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
