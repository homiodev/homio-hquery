package org.homio.hquery.hardware.network;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.homio.hquery.api.ListParse.LineParse;

@Getter
@Setter
@ToString
@Accessors(chain = true)
public class NetworkDescription {

  public static final String IP_PATTERN =
      "((?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))";
  public static final String MAC_PATTERN = "((?:([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})))";

  public static final String IP_RANGE_PATTERN =
      IP_PATTERN + "-(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

  @LineParse("inet " + IP_PATTERN + ".*")
  private String inet;

  @LineParse("ether " + MAC_PATTERN + ".*")
  private String mac;

  @LineParse(".*netmask " + IP_PATTERN + ".*")
  private String netmask;

  @LineParse(".*broadcast " + IP_PATTERN + ".*")
  private String broadcast;
}
