package cn.hcy.gopan;

import org.junit.Test;
import static org.junit.Assert.*;

public class TransferRateTest {
    @Test public void resumedBytesAreNotCountedAsNewTraffic() {
        TransferRate rate = new TransferRate();
        assertEquals(0, rate.sample(20_000_000, 1000));
        assertEquals(2_000_000, rate.sample(22_000_000, 2000));
        assertEquals(0, rate.sample(1_000_000, 3000));
        assertEquals(1_000_000, rate.sample(2_000_000, 4000));
    }

    @Test public void shortSamplesDoNotProduceSpeedSpikes() {
        TransferRate rate = new TransferRate();
        rate.sample(0, 0);
        assertEquals(0, rate.sample(900_000, 100));
        assertEquals(1_000_000, rate.sample(1_000_000, 1000));
        assertEquals(825_000, rate.sample(2_000_000, 3000));
    }

    @Test public void unknownAndFinishedRemainingTimeAreHonest() {
        assertEquals(-1, TransferRate.remaining(10, 0, 0));
        assertEquals(-1, TransferRate.remaining(0, 0, 10));
        assertEquals(4, TransferRate.remaining(10, 0, 3));
        assertEquals(0, TransferRate.remaining(10, 12, 3));
        assertEquals("正在计算", TransferRate.time(-1));
        assertEquals("约 2 分钟", TransferRate.time(61));
    }

    @Test public void unicodeFilenameKeepsWholeCodePointsAndExtension() {
        String original="📷".repeat(32)+".mp4";
        String result=Notices.compactName(original);
        assertTrue(result.endsWith(".mp4"));
        assertEquals(24,result.codePointCount(0,result.length()));
        assertFalse(result.contains("\uFFFD"));
    }
}
