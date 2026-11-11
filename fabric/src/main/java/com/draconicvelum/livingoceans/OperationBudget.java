// SPDX-License-Identifier: GPL-3.0-only
package com.draconicvelum.livingoceans;

/** Each explicitly charged world read or write consumes one operation. */
public final class OperationBudget {
    private final int limit;
    private int used;
    public OperationBudget(int limit) { this.limit = limit; }
    public boolean take() { if (used >= limit) return false; used++; return true; }
    public boolean take(int count) { if(count<0||count>limit-used)return false;used+=count;return true; }
    public int used() { return used; }
    public int remaining() { return limit-used; }
}
