package com.softwareag.naturalone.natural.paltransactions.external;

/**
 * Schnittstelle für Arabic/BiDi-Shaping.
 */
public interface IPalArabicShaping {

    String unshape(String text);

    String shapeIBM420(String text);
}

