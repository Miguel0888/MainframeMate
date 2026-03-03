package de.bund.zrb.ndv.transaction.api;

import de.bund.zrb.ndv.core.api.*;

import java.io.Serializable;

/**
 * Schnittstelle für Natural-Parameter.
 */
public interface INatParm extends Serializable {

    IReport getReport();

    ICharAssign getCharAssign();

    IFldApp getFldApp();

    ICompOpt getCompOpt();

    ILimit getLimit();

    IRegional getRegional();

    IRpc getRpc();

    IBuffSize getBuffSize();

    IErr getErr();

    IPalTypeNatParm[] get(int index);
}

