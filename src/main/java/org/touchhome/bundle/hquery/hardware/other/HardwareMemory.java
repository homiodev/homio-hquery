package org.touchhome.bundle.hquery.hardware.other;

import org.touchhome.bundle.hquery.api.SplitParse;

@SplitParse("\\s+")
public class HardwareMemory {

    @SplitParse.SplitParseIndex(index = 1)
    public Integer size;

    @SplitParse.SplitParseIndex(index = 2)
    public Integer used;

    @SplitParse.SplitParseIndex(index = 3)
    public Integer available;

    @SplitParse.SplitParseIndex(index = 4)
    public String usedPercentage;

    public String toString() {
        return String.format("%d/%d Mb (%s)", used, size, usedPercentage);
    }
}
