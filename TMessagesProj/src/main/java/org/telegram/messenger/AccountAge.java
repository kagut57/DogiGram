/*
 * DogiGram: estimate when a Telegram account was registered.
 *
 * Telegram does not expose an account's registration date through its public API. However user ids
 * are handed out (roughly) in increasing order over time, so the creation date can be *approximated*
 * by interpolating a user's id against a table of known id/date anchor points. This is the same
 * technique used by community "account age" bots and other Telegram clients, and the result is only
 * an estimate — it is always presented to the user as approximate.
 */
package org.telegram.messenger;

public class AccountAge {

    // Anchor points: { userId, unixSeconds }. Kept strictly increasing on both axes. Values are the
    // commonly-referenced community approximations for when each id range was reached.
    private static final long[][] ANCHORS = {
            {1L,             1376438400L}, // 2013-08-14
            {10_000_000L,    1401580800L}, // 2014-06-01
            {50_000_000L,    1420070400L}, // 2015-01-01
            {100_000_000L,   1470009600L}, // 2016-08-01
            {200_000_000L,   1493596800L}, // 2017-05-01
            {300_000_000L,   1512086400L}, // 2017-12-01
            {400_000_000L,   1527811200L}, // 2018-06-01
            {600_000_000L,   1546300800L}, // 2019-01-01
            {800_000_000L,   1564617600L}, // 2019-08-01
            {1_000_000_000L, 1580515200L}, // 2020-02-01
            {1_200_000_000L, 1604188800L}, // 2020-11-01
            {1_400_000_000L, 1619827200L}, // 2021-05-01
            {1_600_000_000L, 1635724800L}, // 2021-11-01
            {2_000_000_000L, 1651363200L}, // 2022-05-01
            {5_000_000_000L, 1661990400L}, // 2022-09-01
            {5_500_000_000L, 1672531200L}, // 2023-01-01
            {6_000_000_000L, 1690848000L}, // 2023-08-01
            {6_500_000_000L, 1709251200L}, // 2024-03-01
            {7_000_000_000L, 1727740800L}, // 2024-10-01
            {7_600_000_000L, 1748736000L}, // 2025-06-01
    };

    /**
     * Estimate the registration time (unix seconds, UTC) for the given user id. Returns 0 if the id
     * is not usable. The value is an approximation; callers must present it as such.
     */
    public static long estimateUnixTime(long userId) {
        if (userId <= 0) {
            return 0;
        }
        if (userId <= ANCHORS[0][0]) {
            return ANCHORS[0][1];
        }
        if (userId >= ANCHORS[ANCHORS.length - 1][0]) {
            return ANCHORS[ANCHORS.length - 1][1];
        }
        for (int i = 1; i < ANCHORS.length; i++) {
            if (userId <= ANCHORS[i][0]) {
                long id0 = ANCHORS[i - 1][0], id1 = ANCHORS[i][0];
                long t0 = ANCHORS[i - 1][1], t1 = ANCHORS[i][1];
                double ratio = (double) (userId - id0) / (double) (id1 - id0);
                return t0 + Math.round(ratio * (t1 - t0));
            }
        }
        return ANCHORS[ANCHORS.length - 1][1];
    }

    // True when the id is newer than our newest anchor, so the estimate is a lower bound only.
    public static boolean isBeyondTable(long userId) {
        return userId >= ANCHORS[ANCHORS.length - 1][0];
    }
}
