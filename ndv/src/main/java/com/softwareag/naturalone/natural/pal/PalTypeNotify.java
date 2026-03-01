package com.softwareag.naturalone.natural.pal;

import com.softwareag.naturalone.natural.pal.external.IPalTypeNotify;

public final class PalTypeNotify extends PalType implements IPalTypeNotify {
    private static final long serialVersionUID = 1L;
    private int notification;
    private int extension;

    public PalTypeNotify() { super(); type = 19; }
    public PalTypeNotify(int notification) { this(); this.notification = notification; }

    public void serialize() { intToBuffer(notification); intToBuffer(extension); }
    public void restore() { notification = intFromBuffer(); extension = intFromBuffer(); }

    public int getNotification() { return notification; }
}
