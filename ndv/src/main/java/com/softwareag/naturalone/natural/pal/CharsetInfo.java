package com.softwareag.naturalone.natural.pal;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class CharsetInfo {
    private Charset zeichensatz;
    private CharsetDecoder dekodierer;
    private CharsetEncoder kodierer;

    public CharsetInfo(Charset charset, CharsetDecoder decoder, CharsetEncoder encoder) {
        this.zeichensatz = charset;
        this.dekodierer = decoder;
        this.kodierer = encoder;
    }

    public Charset getCharset() {
        return zeichensatz;
    }

    public CharsetDecoder getDecoder() {
        return dekodierer;
    }

    public CharsetEncoder getEncoder() {
        return kodierer;
    }
}
