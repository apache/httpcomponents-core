/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.hc.core5.util;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

/**
 * Like {@link TimeUnit} but with {@link BigInteger}.
 */
enum BigIntegerTimeUnit {

    NANOSECONDS {
        @Override
        public BigInteger toNanos(final BigInteger d) {
            return d;
        }

        @Override
        public BigInteger toMicros(final BigInteger d) {
            return d.divide(C1.divide(C0));
        }

        @Override
        public BigInteger toMillis(final BigInteger d) {
            return d.divide(C2.divide(C0));
        }

        @Override
        public BigInteger toSeconds(final BigInteger d) {
            return d.divide(C3.divide(C0));
        }

        @Override
        public BigInteger toMinutes(final BigInteger d) {
            return d.divide(C4.divide(C0));
        }

        @Override
        public BigInteger toHours(final BigInteger d) {
            return d.divide(C5.divide(C0));
        }

        @Override
        public BigInteger toDays(final BigInteger d) {
            return d.divide(C6.divide(C0));
        }

        @Override
        public BigInteger convert(final BigInteger d, final BigIntegerTimeUnit u) {
            return u.toNanos(d);
        }

        @Override
        int excessNanos(final BigInteger d, final BigInteger m) {
            return (d.subtract(m.multiply(C2))).intValue();
        }
    },

    MICROSECONDS {
        @Override
        public BigInteger toNanos(final BigInteger d) {
            return x(d, C1.divide(C0));
        }

        @Override
        public BigInteger toMicros(final BigInteger d) {
            return d;
        }

        @Override
        public BigInteger toMillis(final BigInteger d) {
            return d.divide(C2.divide(C1));
        }

        @Override
        public BigInteger toSeconds(final BigInteger d) {
            return d.divide(C3.divide(C1));
        }

        @Override
        public BigInteger toMinutes(final BigInteger d) {
            return d.divide(C4.divide(C1));
        }

        @Override
        public BigInteger toHours(final BigInteger d) {
            return d.divide(C5.divide(C1));
        }

        @Override
        public BigInteger toDays(final BigInteger d) {
            return d.divide(C6.divide(C1));
        }

        @Override
        public BigInteger convert(final BigInteger d, final BigIntegerTimeUnit u) {
            return u.toMicros(d);
        }

        @Override
        int excessNanos(final BigInteger d, final BigInteger m) {
            return ((d.multiply(C1)).subtract(m.multiply(C2))).intValue();
        }
    },

    MILLISECONDS {
        @Override
        public BigInteger toNanos(final BigInteger d) {
            return x(d, C2.divide(C0));
        }

        @Override
        public BigInteger toMicros(final BigInteger d) {
            return x(d, C2.divide(C1));
        }

        @Override
        public BigInteger toMillis(final BigInteger d) {
            return d;
        }

        @Override
        public BigInteger toSeconds(final BigInteger d) {
            return d.divide(C3.divide(C2));
        }

        @Override
        public BigInteger toMinutes(final BigInteger d) {
            return d.divide(C4.divide(C2));
        }

        @Override
        public BigInteger toHours(final BigInteger d) {
            return d.divide(C5.divide(C2));
        }

        @Override
        public BigInteger toDays(final BigInteger d) {
            return d.divide(C6.divide(C2));
        }

        @Override
        public BigInteger convert(final BigInteger d, final BigIntegerTimeUnit u) {
            return u.toMillis(d);
        }

        @Override
        int excessNanos(final BigInteger d, final BigInteger m) {
            return 0;
        }
    },

    SECONDS {
        @Override
        public BigInteger toNanos(final BigInteger d) {
            return x(d, C3.divide(C0));
        }

        @Override
        public BigInteger toMicros(final BigInteger d) {
            return x(d, C3.divide(C1));
        }

        @Override
        public BigInteger toMillis(final BigInteger d) {
            return x(d, C3.divide(C2));
        }

        @Override
        public BigInteger toSeconds(final BigInteger d) {
            return d;
        }

        @Override
        public BigInteger toMinutes(final BigInteger d) {
            return d.divide(C4.divide(C3));
        }

        @Override
        public BigInteger toHours(final BigInteger d) {
            return d.divide(C5.divide(C3));
        }

        @Override
        public BigInteger toDays(final BigInteger d) {
            return d.divide(C6.divide(C3));
        }

        @Override
        public BigInteger convert(final BigInteger d, final BigIntegerTimeUnit u) {
            return u.toSeconds(d);
        }

        @Override
        int excessNanos(final BigInteger d, final BigInteger m) {
            return 0;
        }
    },

    MINUTES {
        @Override
        public BigInteger toNanos(final BigInteger d) {
            return x(d, C4.divide(C0));
        }

        @Override
        public BigInteger toMicros(final BigInteger d) {
            return x(d, C4.divide(C1));
        }

        @Override
        public BigInteger toMillis(final BigInteger d) {
            return x(d, C4.divide(C2));
        }

        @Override
        public BigInteger toSeconds(final BigInteger d) {
            return x(d, C4.divide(C3));
        }

        @Override
        public BigInteger toMinutes(final BigInteger d) {
            return d;
        }

        @Override
        public BigInteger toHours(final BigInteger d) {
            return d.divide(C5.divide(C4));
        }

        @Override
        public BigInteger toDays(final BigInteger d) {
            return d.divide(C6.divide(C4));
        }

        @Override
        public BigInteger convert(final BigInteger d, final BigIntegerTimeUnit u) {
            return u.toMinutes(d);
        }

        @Override
        int excessNanos(final BigInteger d, final BigInteger m) {
            return 0;
        }
    },

    HOURS {
        @Override
        public BigInteger toNanos(final BigInteger d) {
            return x(d, C5.divide(C0));
        }

        @Override
        public BigInteger toMicros(final BigInteger d) {
            return x(d, C5.divide(C1));
        }

        @Override
        public BigInteger toMillis(final BigInteger d) {
            return x(d, C5.divide(C2));
        }

        @Override
        public BigInteger toSeconds(final BigInteger d) {
            return x(d, C5.divide(C3));
        }

        @Override
        public BigInteger toMinutes(final BigInteger d) {
            return x(d, C5.divide(C4));
        }

        @Override
        public BigInteger toHours(final BigInteger d) {
            return d;
        }

        @Override
        public BigInteger toDays(final BigInteger d) {
            return d.divide(C6.divide(C5));
        }

        @Override
        public BigInteger convert(final BigInteger d, final BigIntegerTimeUnit u) {
            return u.toHours(d);
        }

        @Override
        int excessNanos(final BigInteger d, final BigInteger m) {
            return 0;
        }
    },

    DAYS {
        @Override
        public BigInteger toNanos(final BigInteger d) {
            return x(d, C6.divide(C0));
        }

        @Override
        public BigInteger toMicros(final BigInteger d) {
            return x(d, C6.divide(C1));
        }

        @Override
        public BigInteger toMillis(final BigInteger d) {
            return x(d, C6.divide(C2));
        }

        @Override
        public BigInteger toSeconds(final BigInteger d) {
            return x(d, C6.divide(C3));
        }

        @Override
        public BigInteger toMinutes(final BigInteger d) {
            return x(d, C6.divide(C4));
        }

        @Override
        public BigInteger toHours(final BigInteger d) {
            return x(d, C6.divide(C5));
        }

        @Override
        public BigInteger toDays(final BigInteger d) {
            return d;
        }

        @Override
        public BigInteger convert(final BigInteger d, final BigIntegerTimeUnit u) {
            return u.toDays(d);
        }

        @Override
        int excessNanos(final BigInteger d, final BigInteger m) {
            return 0;
        }
    };

    private static final BigInteger BI_1000 = BigInteger.valueOf(1000);
    private static final BigInteger BI_60 = BigInteger.valueOf(60);
    private static final BigInteger BI_24 = BigInteger.valueOf(24);
    private static final BigInteger C0 = BigInteger.ONE;
    private static final BigInteger C1 = C0.multiply(BI_1000);
    private static final BigInteger C2 = C1.multiply(BI_1000);
    private static final BigInteger C3 = C2.multiply(BI_1000);
    private static final BigInteger C4 = C3.multiply(BI_60);
    private static final BigInteger C5 = C4.multiply(BI_60);
    private static final BigInteger C6 = C5.multiply(BI_24);

    /**
     * A short name to make call sites more readable.
     */
    private static BigInteger x(final BigInteger d, final BigInteger m) {
        return d.multiply(m);
    }

    /**
     * Converts a standard TimeUnit to an BigIntegerTimeUnit.
     * @param u a standard TimeUnit.
     * @return
     */
    static BigIntegerTimeUnit toBigIntegerTimeUnit(final TimeUnit u) {
        switch (u) {
        case DAYS:
            return DAYS;
        case HOURS:
            return HOURS;
        case MINUTES:
            return MINUTES;
        case SECONDS:
            return SECONDS;
        case MILLISECONDS:
            return MILLISECONDS;
        case MICROSECONDS:
            return MICROSECONDS;
        case NANOSECONDS:
            return NANOSECONDS;
        default:
            throw new IllegalArgumentException(u.toString());
        }
    }

    public abstract BigInteger convert(final BigInteger sourceDuration, final BigIntegerTimeUnit sourceUnit);

    public abstract BigInteger toNanos(final BigInteger duration);

    public BigInteger toNanos(final long duration) {
        return toNanos(BigInteger.valueOf(duration));
    }

    public abstract BigInteger toMicros(final BigInteger duration);

    public BigInteger toMicros(final long duration) {
        return toMicros(BigInteger.valueOf(duration));
    }

    public abstract BigInteger toMillis(final BigInteger duration);

    public BigInteger toMillis(final long duration) {
        return toMillis(BigInteger.valueOf(duration));
    }

    public abstract BigInteger toSeconds(final BigInteger duration);

    public BigInteger toSeconds(final long duration) {
        return toSeconds(BigInteger.valueOf(duration));
    }

    public abstract BigInteger toMinutes(final BigInteger duration);

    public BigInteger toMinutes(final long duration) {
        return toMinutes(BigInteger.valueOf(duration));
    }

    public abstract BigInteger toHours(final BigInteger duration);

    public BigInteger toHours(final long duration) {
        return toHours(BigInteger.valueOf(duration));
    }

    public abstract BigInteger toDays(final BigInteger duration);

    public BigInteger toDays(final long duration) {
        return toDays(BigInteger.valueOf(duration));
    }

    abstract int excessNanos(BigInteger d, BigInteger m);

    public void timedWait(final Object obj, final BigInteger timeout) throws InterruptedException {
        if (timeout.compareTo(BigInteger.ZERO) > 0) {
            final BigInteger ms = toMillis(timeout);
            final int ns = excessNanos(timeout, ms);
            obj.wait(ms.longValue(), ns);
        }
    }
    public void timedJoin(final Thread thread, final BigInteger timeout) throws InterruptedException {
        if (timeout.compareTo(BigInteger.ZERO) > 0) {
            final BigInteger ms = toMillis(timeout);
            final int ns = excessNanos(timeout, ms);
            thread.join(ms.longValue(), ns);
        }
    }

    public void sleep(final BigInteger timeout) throws InterruptedException {
        if (timeout.compareTo(BigInteger.ZERO) > 0) {
            final BigInteger ms = toMillis(timeout);
            final int ns = excessNanos(timeout, ms);
            Thread.sleep(ms.longValue(), ns);
        }
    }

}
