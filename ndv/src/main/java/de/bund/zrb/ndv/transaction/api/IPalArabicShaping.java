package de.bund.zrb.ndv.transaction.api;

/**
 * Schnittstelle für Arabic/BiDi-Shaping.
 */
public interface IPalArabicShaping {

    String unshape(String text);

    String shapeIBM420(String text);
}

