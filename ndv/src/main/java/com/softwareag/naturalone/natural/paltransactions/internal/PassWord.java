package com.softwareag.naturalone.natural.paltransactions.internal;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

/**
 * Verschlüsselung der Anmeldedaten für das NDV-Protokoll.
 * <p>
 * Kodiert Benutzerkennung, Passwort, Bibliothek und (optionales) neues Passwort
 * zu einem einzelnen hex-kodierten Token, das beim Verbindungsaufbau zum
 * NDV-Server gesendet wird.
 * <p>
 * Der Algorithmus verwendet BCD-Arithmetik (Binary Coded Decimal), um die
 * Anmeldebytes mit einem zeitbasierten Schlüssel zu verwürfeln.
 */
public final class PassWord {

    // --- Plattform-Kennungen ---
    private static final int PLATTFORM_ASCII = 1;
    private static final int PLATTFORM_EBCDIC = 2;

    // --- BCD-Konstanten ---
    private static final int HALBBYTE_UNTERE_MASKE = 0x0F;  // 15
    private static final int HALBBYTE_OBERE_MASKE = 0xF0;   // 240
    private static final int BCD_ERFOLG = 0;
    private static final int BCD_UEBERLAUF = 1305;
    private static final int BCD_DIVISION_DURCH_NULL = 1302;
    private static final int BCD_VORZEICHEN_MINUS = 13;
    private static final int BCD_VORZEICHEN_PLUS = 12;

    /** Erkannte Plattform (ASCII oder EBCDIC). */
    private static int erkannePlattform;

    private PassWord() {
    }

    // =================================================================
    //  Öffentliche Schnittstelle
    // =================================================================

    /**
     * Kodiert die Anmeldedaten für den NDV-Verbindungsaufbau.
     *
     * @param userId      Benutzerkennung (max. 8 Zeichen)
     * @param password    Passwort (max. 8 Zeichen)
     * @param library     Bibliotheksname (max. 8 Zeichen)
     * @param newPassword neues Passwort (darf leer sein)
     * @return hex-kodiertes Token in Großbuchstaben
     */
    public static String encode(String userId, String password, String library, String newPassword) {
        char[] hexBenutzer = new char[17];
        char[] hexPasswort = new char[17];
        char[] hexBibliothek = new char[17];
        char[] hexNeuesPasswort = new char[17];
        char[] ergebnis = new char[74];
        byte[] arbeitsPuffer = new byte[9];
        byte[] benutzerBytes = new byte[9];
        byte[] passwortBytes = new byte[9];
        byte[] zeitBytes = new byte[9];
        byte[] bibliothekBytes = new byte[9];
        byte[] neuesPasswortBytes = new byte[9];

        // Aktuelle Uhrzeit als Verwürfelungsschlüssel – Format "HH0mm0ss"
        String zeitText = (new SimpleDateFormat("HH0mm0ss"))
                .format((new GregorianCalendar()).getTime());

        plattformErkennen();

        if (userId.length() > 8) {
            throw new IllegalArgumentException("the user id " + userId + " exceeds 8 bytes");
        }
        if (password.length() > 8) {
            throw new IllegalArgumentException("the password " + password + " exceeds 8 bytes");
        }
        if (library.length() > 8) {
            throw new IllegalArgumentException("the library " + library + " exceeds 8 bytes");
        }

        // Ergebnis mit Leerzeichen füllen
        for (int i = 0; i < 74; i++) {
            ergebnis[i] = ' ';
        }

        // Byte-Felder initialisieren
        benutzerBytes[0] = 0;
        passwortBytes[0] = 0;
        bibliothekBytes[0] = 0;
        neuesPasswortBytes[0] = 0;

        // Anmeldedaten in Festlängen-Puffer kopieren
        byte[] zwischenspeicher = userId.getBytes();
        System.arraycopy(zwischenspeicher, 0, benutzerBytes, 0, zwischenspeicher.length);

        zwischenspeicher = password.getBytes();
        System.arraycopy(zwischenspeicher, 0, passwortBytes, 0, zwischenspeicher.length);

        zwischenspeicher = zeitText.getBytes();
        System.arraycopy(zwischenspeicher, 0, zeitBytes, 0, zwischenspeicher.length);

        zwischenspeicher = library.getBytes();
        System.arraycopy(zwischenspeicher, 0, bibliothekBytes, 0, zwischenspeicher.length);

        zwischenspeicher = newPassword.getBytes();
        System.arraycopy(zwischenspeicher, 0, neuesPasswortBytes, 0, zwischenspeicher.length);

        // Benutzerkennung verwürfeln
        System.arraycopy(benutzerBytes, 0, arbeitsPuffer, 0, 8);
        zeitVerwuerfeln(arbeitsPuffer, zeitBytes);
        hexBenutzer = bytesZuHexZeichen(arbeitsPuffer);

        // Passwort verwürfeln
        System.arraycopy(passwortBytes, 0, arbeitsPuffer, 0, 8);
        arbeitsPuffer = zeitVerwuerfeln(arbeitsPuffer, zeitBytes);
        hexPasswort = bytesZuHexZeichen(arbeitsPuffer);

        // Bibliothek verwürfeln
        System.arraycopy(bibliothekBytes, 0, arbeitsPuffer, 0, 8);
        arbeitsPuffer = zeitVerwuerfeln(arbeitsPuffer, zeitBytes);
        hexBibliothek = bytesZuHexZeichen(arbeitsPuffer);

        // Neues Passwort verwürfeln (falls vorhanden)
        if (neuesPasswortBytes.length > 0) {
            System.arraycopy(neuesPasswortBytes, 0, arbeitsPuffer, 0, 8);
            zeitVerwuerfeln(arbeitsPuffer, zeitBytes);
            hexNeuesPasswort = bytesZuHexZeichen(arbeitsPuffer);
        }

        // Token zusammenbauen
        // Position 0: Plattform-Kennung
        if (erkannePlattform == PLATTFORM_ASCII) {
            ergebnis[0] = 'C';
        }
        if (erkannePlattform == PLATTFORM_EBCDIC) {
            ergebnis[0] = 'E';
        }

        // Position 1..8: Zeitschlüssel
        char[] zeitZeichen = new char[9];
        for (int i = 0; i < 8; i++) {
            zeitZeichen[i] = (char) zeitBytes[i];
        }
        System.arraycopy(zeitZeichen, 0, ergebnis, 1, 8);

        // Position 9..24: Benutzer-Hex
        System.arraycopy(hexBenutzer, 0, ergebnis, 9, 16);
        // Position 25..40: Passwort-Hex
        System.arraycopy(hexPasswort, 0, ergebnis, 25, 16);
        // Position 41..56: Bibliothek-Hex
        System.arraycopy(hexBibliothek, 0, ergebnis, 41, 16);

        if (neuesPasswortBytes.length == 0) {
            ergebnis[57] = 255;
        } else {
            System.arraycopy(hexNeuesPasswort, 0, ergebnis, 57, 16);
            ergebnis[73] = 255;
        }

        return (new String(ergebnis)).toUpperCase();
    }

    // =================================================================
    //  Plattformerkennung
    // =================================================================

    /**
     * Erkennt ob die aktuelle Laufzeitumgebung ASCII oder EBCDIC verwendet.
     * Ein Leerzeichen ist in ASCII 0x20 (32), in EBCDIC 0x40 (64).
     */
    private static void plattformErkennen() {
        byte leerzeichen = 32;
        erkannePlattform = 0;
        if (leerzeichen == 32) {
            erkannePlattform = PLATTFORM_ASCII;
        }
        if (leerzeichen == 64) {
            erkannePlattform = PLATTFORM_EBCDIC;
        }
    }

    // =================================================================
    //  Kern-Verwürfelung
    // =================================================================

    /**
     * Verwürfelt 8 Anmeldebytes mit einem zeitbasierten BCD-Schlüssel.
     * <p>
     * Ablauf:
     * <ol>
     *   <li>Die Zeitstempel-Ziffern werden umgeordnet zu einem 6-stelligen Startwert.</li>
     *   <li>Für jedes der 8 Anmeldebytes:<br>
     *       – Berechne {@code startwert = (startwert × MULTIPLIKATOR) mod GANZZAHL_MAXIMUM}<br>
     *       – Extrahiere den Rest modulo 1000<br>
     *       – Addiere diesen Rest auf das Anmeldebyte.</li>
     * </ol>
     *
     * @param daten          die 8 Anmeldebytes (werden in-place überschrieben)
     * @param zeitSchluessel der 8-Byte-Zeitstempel als Verwürfelungsbasis
     * @return Referenz auf das modifizierte {@code daten}-Array
     */
    private static byte[] zeitVerwuerfeln(byte[] daten, byte[] zeitSchluessel) {
        byte[] zeitKopie = new byte[9];
        byte[] datenKopie = new byte[9];
        byte[] fuellPuffer = new byte[]{32, 32, 32, 32, 32, 32, 32, 32, 32};

        char[] startwertZiffern = new char[6];
        int letzterIndex = 7;          // Index des letzten Bytes (0..7)
        int bcdArbeitsLaenge = 20;     // BCD-Arbeitsbreite in Halbbytes

        long startwert;
        long rest;
        long ganzzahlMaximum = 2147483647L;
        long multiplikator = 455470314L;

        byte[] bcdTausend = new byte[20];
        byte[] bcdMultiplikator = new byte[20];
        byte[] bcdStartwert = new byte[20];
        byte[] bcdMaximum = new byte[20];
        byte[] bcdProdukt = new byte[20];
        byte[] bcdQuotient = new byte[20];
        byte[] bcdGanzteilRest = new byte[20];
        byte[] bcdModuloRest = new byte[20];

        System.arraycopy(daten, 0, datenKopie, 0, letzterIndex + 1);
        System.arraycopy(zeitSchluessel, 0, zeitKopie, 0, letzterIndex + 1);

        // Zeitstempel-Ziffern umordnen zum Startwert
        startwertZiffern[0] = (char) zeitKopie[4];
        startwertZiffern[1] = (char) zeitKopie[1];
        startwertZiffern[2] = (char) zeitKopie[3];
        startwertZiffern[3] = (char) zeitKopie[6];
        startwertZiffern[4] = (char) zeitKopie[7];
        startwertZiffern[5] = (char) zeitKopie[0];

        startwert = (long) Integer.parseInt(new String(startwertZiffern));

        // BCD-Konstanten initialisieren
        ganzzahlNachBcd(bcdTausend, bcdArbeitsLaenge, 1000L);
        ganzzahlNachBcd(bcdMultiplikator, bcdArbeitsLaenge, multiplikator);
        ganzzahlNachBcd(bcdStartwert, bcdArbeitsLaenge, startwert);
        ganzzahlNachBcd(bcdMaximum, bcdArbeitsLaenge, ganzzahlMaximum);

        // Jedes Byte einzeln verwürfeln
        for (int i = 0; i <= letzterIndex; i++) {
            // produkt = multiplikator × startwert
            System.arraycopy(bcdMultiplikator, 0, bcdProdukt, 0, 20);
            bcdMultiplizieren(bcdProdukt, bcdArbeitsLaenge, bcdStartwert, bcdArbeitsLaenge);

            // quotient = produkt / maximum (ganzzahliger Quotient)
            System.arraycopy(bcdProdukt, 0, bcdQuotient, 0, 20);
            bcdDividieren(bcdQuotient, bcdArbeitsLaenge, bcdMaximum, bcdArbeitsLaenge);

            // quotient = quotient × maximum → floor(produkt/maximum) × maximum
            bcdMultiplizieren(bcdQuotient, bcdArbeitsLaenge, bcdMaximum, bcdArbeitsLaenge);

            // startwert = produkt − quotient → (multiplikator × startwert) mod maximum
            System.arraycopy(bcdProdukt, 0, bcdStartwert, 0, 20);
            bcdSubtrahieren(bcdStartwert, bcdArbeitsLaenge, bcdQuotient, bcdArbeitsLaenge);

            // ganzteilRest = startwert / 1000 (ganzzahliger Quotient)
            System.arraycopy(bcdStartwert, 0, bcdGanzteilRest, 0, 20);
            bcdDividieren(bcdGanzteilRest, bcdArbeitsLaenge, bcdTausend, bcdArbeitsLaenge);

            // ganzteilRest = ganzteilRest × 1000
            bcdMultiplizieren(bcdGanzteilRest, bcdArbeitsLaenge, bcdTausend, bcdArbeitsLaenge);

            // moduloRest = startwert − ganzteilRest → startwert mod 1000
            System.arraycopy(bcdStartwert, 0, bcdModuloRest, 0, 20);
            bcdSubtrahieren(bcdModuloRest, bcdArbeitsLaenge, bcdGanzteilRest, bcdArbeitsLaenge);

            // BCD-Rest zurück in Ganzzahl wandeln
            rest = bcdNachGanzzahl(bcdModuloRest, bcdArbeitsLaenge);

            // Rest auf das Anmeldebyte addieren
            long wert = (long) datenKopie[i];
            wert += rest;
            fuellPuffer[i] = (byte) ((int) wert);
        }

        // Ergebnis zurückkopieren
        for (int i = 0; i <= letzterIndex; i++) {
            daten[i] = fuellPuffer[i];
        }

        return daten;
    }

    // =================================================================
    //  BCD-Arithmetik
    // =================================================================

    /**
     * BCD-Multiplikation: ergebnis = ergebnis × faktor (gepackte Dezimalzahl).
     *
     * @param ergebnis       Ziel-/Ergebnis-Array (wird in-place überschrieben)
     * @param ergebnisLaenge Anzahl der Dezimalstellen im Ergebnis
     * @param faktor         der zweite Faktor (nur gelesen)
     * @param faktorLaenge   Anzahl der Dezimalstellen im Faktor
     * @return {@link #BCD_ERFOLG} oder {@link #BCD_UEBERLAUF}
     */
    private static int bcdMultiplizieren(byte[] ergebnis, int ergebnisLaenge,
                                         byte[] faktor, int faktorLaenge) {
        boolean ueberlauf = false;
        boolean vorzeichenErgebnisPositiv = false;
        boolean vorzeichenFaktorPositiv = false;

        int auffuellungA = 1 - ergebnisLaenge % 2;
        int endeA = ergebnisLaenge + auffuellungA;
        int auffuellungB = 1 - faktorLaenge % 2;
        int endeB = faktorLaenge + auffuellungB;

        // Signifikante Stellen zählen um Überlauf vorherzusagen
        int stelle;
        for (stelle = auffuellungA; stelle < endeA && halbbyteAuslesen(ergebnis, stelle) == 0; ++stelle) {
        }
        int signifikanteStellen = ergebnisLaenge - stelle;

        for (stelle = auffuellungB; stelle < endeB && halbbyteAuslesen(faktor, stelle) == 0; ++stelle) {
        }
        signifikanteStellen += faktorLaenge - stelle;

        if (signifikanteStellen > ergebnisLaenge + 1) {
            ueberlauf = true;
        }

        // Stellenweise Multiplikation
        for (int i = auffuellungA; i < endeA; ++i) {
            int summe = 0;
            int posA = i;

            for (int posB = endeB - 1; posA < endeA && posB >= auffuellungB; --posB) {
                int ziffer = halbbyteAuslesen(ergebnis, posA);
                ziffer *= halbbyteAuslesen(faktor, posB);
                summe += ziffer;
                ++posA;
            }

            int uebertrag = summe / 10;
            ergebnis = halbbyteSchreiben(ergebnis, i, (long) (summe % 10));

            if (uebertrag > 0) {
                for (int k = i - 1; k >= auffuellungA && uebertrag != 0; --k) {
                    summe = halbbyteAuslesen(ergebnis, k) + uebertrag;
                    uebertrag = summe / 10;
                    ergebnis = halbbyteSchreiben(ergebnis, k, (long) (summe % 10));
                }
                if (uebertrag != 0) {
                    ueberlauf = true;
                }
            }
        }

        if (ueberlauf) {
            return BCD_UEBERLAUF;
        }

        // Vorzeichen des Ergebnisses bestimmen
        int nullPruefung = bcdIstNull(ergebnis, ergebnisLaenge);
        if (nullPruefung != 0 || istVorzeichenPositiv(halbbyteAuslesen(ergebnis, endeA))) {
            vorzeichenErgebnisPositiv = true;
        }
        if (bcdIstNull(faktor, faktorLaenge) != 0 || istVorzeichenPositiv(halbbyteAuslesen(faktor, endeB))) {
            vorzeichenFaktorPositiv = true;
        }
        if (nullPruefung == 0 && vorzeichenErgebnisPositiv != vorzeichenFaktorPositiv) {
            vorzeichenNegativSetzen(ergebnis, endeA);
        } else {
            vorzeichenPositivSetzen(ergebnis, endeA);
        }

        return BCD_ERFOLG;
    }

    /**
     * BCD-Division: dividend = dividend / divisor (ganzzahliger Quotient, gepackte Dezimalzahl).
     *
     * @param dividend       Ziel-/Ergebnis-Array (wird in-place überschrieben)
     * @param dividendLaenge Anzahl der Dezimalstellen im Dividend
     * @param divisor        der Divisor (nur gelesen)
     * @param divisorLaenge  Anzahl der Dezimalstellen im Divisor
     * @return {@link #BCD_ERFOLG} oder {@link #BCD_DIVISION_DURCH_NULL}
     */
    private static int bcdDividieren(byte[] dividend, int dividendLaenge,
                                     byte[] divisor, int divisorLaenge) {
        int ersteDivisorZiffer = 0;
        byte[] quotient = new byte[21];
        boolean vorzeichenDividendPositiv = false;
        boolean vorzeichenDivisorPositiv = false;

        int auffuellungA = 1 - dividendLaenge % 2;
        int endeA = dividendLaenge + auffuellungA;
        int auffuellungB = 1 - divisorLaenge % 2;
        int endeB = divisorLaenge + auffuellungB;

        // Erste von Null verschiedene Divisor-Ziffer finden
        int stelle;
        for (stelle = auffuellungB; stelle < endeB; ++stelle) {
            ersteDivisorZiffer = halbbyteAuslesen(divisor, stelle);
            if (ersteDivisorZiffer != 0) {
                break;
            }
        }

        int divisorStellen = endeB - stelle;
        if (divisorStellen == 0) {
            return BCD_DIVISION_DURCH_NULL;
        }

        // Zweistellige Schätzung des Divisors
        int zweistelligerDivisor = 10 * ersteDivisorZiffer;
        ++stelle;
        if (stelle < endeB) {
            zweistelligerDivisor += halbbyteAuslesen(divisor, stelle);
        }

        // Schriftliche Division
        for (int i = auffuellungA - 1; i < endeA - divisorStellen; ++i) {
            int schaetzung1 = 0;
            if (i >= auffuellungA) {
                schaetzung1 = 10 * halbbyteAuslesen(dividend, i);
            }
            if (i < endeA - 1) {
                schaetzung1 += halbbyteAuslesen(dividend, i + 1);
            }

            int schaetzung2 = 10 * schaetzung1;
            if (i < endeA - 2) {
                schaetzung2 += halbbyteAuslesen(dividend, i + 2);
            }

            // Quotientenziffer schätzen
            int quotientenZiffer = schaetzung1 / ersteDivisorZiffer;
            if (quotientenZiffer > 9) {
                quotientenZiffer = 9;
            }

            // Überschätzung korrigieren
            for (int probe = schaetzung2 - quotientenZiffer * zweistelligerDivisor;
                 probe < 0; --quotientenZiffer) {
                probe += zweistelligerDivisor;
            }

            // quotientenZiffer × Divisor vom Dividenden subtrahieren
            int borger = 0;
            if (quotientenZiffer > 0) {
                int posA = i + divisorStellen;
                for (int posB = endeB - 1; posA >= auffuellungA && posA >= i && posB >= auffuellungB; --posB) {
                    int differenz = 100 + halbbyteAuslesen(dividend, posA) + borger;
                    differenz -= quotientenZiffer * halbbyteAuslesen(divisor, posB);
                    borger = differenz / 10 - 10;
                    dividend = halbbyteSchreiben(dividend, posA, (long) (differenz % 10));
                    --posA;
                }
                while (posA >= auffuellungA && borger < 0) {
                    int differenz = 10 + halbbyteAuslesen(dividend, posA) + borger;
                    borger = differenz / 10 - 1;
                    dividend = halbbyteSchreiben(dividend, posA, (long) (differenz % 10));
                    --posA;
                }
            }

            // Unterschätzung korrigieren (Divisor zurückaddieren)
            if (borger < 0) {
                --quotientenZiffer;
                borger = 0;
                int posA = i + divisorStellen;
                for (int posB = endeB - 1; posA >= auffuellungA && posA >= i && posB >= auffuellungB; --posB) {
                    int differenz = halbbyteAuslesen(dividend, posA) + borger;
                    differenz += halbbyteAuslesen(divisor, posB);
                    if (differenz > 9) {
                        differenz -= 10;
                        borger = 1;
                    } else {
                        borger = 0;
                    }
                    dividend = halbbyteSchreiben(dividend, posA, (long) differenz);
                    --posA;
                }
                while (posA >= auffuellungA && borger != 0) {
                    int differenz = halbbyteAuslesen(dividend, posA) + borger;
                    borger = differenz / 10;
                    dividend = halbbyteSchreiben(dividend, posA, (long) (differenz % 10));
                    --posA;
                }
            }

            quotient = halbbyteSchreiben(quotient, i + 1, (long) quotientenZiffer);
        }

        // Quotienten in den Dividenden zurückkopieren
        stelle = endeA - 1;
        for (int j = stelle - divisorStellen; j >= auffuellungA - 1; --j) {
            dividend = halbbyteSchreiben(dividend, stelle, (long) halbbyteAuslesen(quotient, j + 1));
            --stelle;
        }
        while (stelle >= auffuellungA) {
            dividend = halbbyteSchreiben(dividend, stelle, 0L);
            --stelle;
        }

        // Vorzeichen des Ergebnisses bestimmen
        int nullPruefung = bcdIstNull(dividend, dividendLaenge);
        if (nullPruefung != 0 || istVorzeichenPositiv(halbbyteAuslesen(dividend, endeA))) {
            vorzeichenDividendPositiv = true;
        }
        if (bcdIstNull(divisor, divisorLaenge) != 0 || istVorzeichenPositiv(halbbyteAuslesen(divisor, endeB))) {
            vorzeichenDivisorPositiv = true;
        }
        if (nullPruefung == 0 && vorzeichenDividendPositiv != vorzeichenDivisorPositiv) {
            vorzeichenNegativSetzen(dividend, endeA);
        } else {
            vorzeichenPositivSetzen(dividend, endeA);
        }

        return BCD_ERFOLG;
    }

    /**
     * BCD-Subtraktion: minuend = minuend − subtrahend (gepackte Dezimalzahl).
     *
     * @param minuend          Ziel-/Ergebnis-Array (wird in-place überschrieben)
     * @param minuendLaenge    Anzahl der Dezimalstellen im Minuend
     * @param subtrahend       der Subtrahend (nur gelesen)
     * @param subtrahendLaenge Anzahl der Dezimalstellen im Subtrahend
     * @return {@link #BCD_ERFOLG} oder {@link #BCD_UEBERLAUF}
     */
    private static int bcdSubtrahieren(byte[] minuend, int minuendLaenge,
                                       byte[] subtrahend, int subtrahendLaenge) {
        byte uebertrag = 0;
        int restlicheStellen = minuendLaenge < subtrahendLaenge ? subtrahendLaenge : minuendLaenge;
        int auffuellungA = 1 - minuendLaenge % 2;
        int posA = minuendLaenge + auffuellungA;
        int auffuellungB = 1 - subtrahendLaenge % 2;
        int posB = subtrahendLaenge + auffuellungB;
        boolean ueberlaufErkannt = false;

        if (istVorzeichenNegativ(halbbyteAuslesen(minuend, posA))
                != istVorzeichenNegativ(halbbyteAuslesen(subtrahend, posB))) {
            // Unterschiedliche Vorzeichen → Beträge addieren
            uebertrag = 0;

            while (true) {
                --restlicheStellen;
                if (restlicheStellen < 0) {
                    // Übertrag propagieren
                    int ziffer;
                    for (; posA > auffuellungA && uebertrag != 0;
                         minuend = halbbyteSchreiben(minuend, posA, (long) ziffer)) {
                        --posA;
                        ziffer = halbbyteAuslesen(minuend, posA) + uebertrag;
                        if (ziffer > 9) {
                            ziffer -= 10;
                            uebertrag = 1;
                        } else {
                            uebertrag = 0;
                        }
                    }

                    // Prüfen ob der Subtrahend noch Nicht-Null-Stellen hat
                    while (posB > auffuellungB) {
                        --posB;
                        if (halbbyteAuslesen(subtrahend, posB) != 0) {
                            ueberlaufErkannt = true;
                            break;
                        }
                    }
                    break;
                }

                --posA;
                --posB;
                int ziffer = halbbyteAuslesen(minuend, posA) + uebertrag;
                ziffer += halbbyteAuslesen(subtrahend, posB);
                if (ziffer > 9) {
                    ziffer -= 10;
                    uebertrag = 1;
                } else {
                    uebertrag = 0;
                }
                minuend = halbbyteSchreiben(minuend, posA, (long) ziffer);
            }
        } else {
            // Gleiche Vorzeichen → Beträge subtrahieren
            byte borger = 0;

            while (true) {
                --restlicheStellen;
                if (restlicheStellen < 0) {
                    // Borger propagieren
                    int ziffer;
                    for (; posA > auffuellungA && borger != 0;
                         minuend = halbbyteSchreiben(minuend, posA, (long) ziffer)) {
                        --posA;
                        ziffer = halbbyteAuslesen(minuend, posA) + borger;
                        if (ziffer < 0) {
                            ziffer += 10;
                            borger = 1;
                        } else {
                            borger = 0;
                        }
                    }

                    // Falls noch Borger übrig: Ergebnis negieren und Vorzeichen umkehren
                    if (borger != 0) {
                        borger = 0;
                        auffuellungA = 1 - minuendLaenge % 2;
                        posA = minuendLaenge + auffuellungA;
                        if (istVorzeichenPositiv(halbbyteAuslesen(minuend, posA))) {
                            minuend = vorzeichenNegativSetzen(minuend, posA);
                        } else {
                            minuend = vorzeichenPositivSetzen(minuend, posA);
                        }

                        int negierteZiffer;
                        for (; posA > auffuellungA;
                             minuend = halbbyteSchreiben(minuend, posA, (long) negierteZiffer)) {
                            --posA;
                            negierteZiffer = -halbbyteAuslesen(minuend, posA) - borger;
                            if (negierteZiffer < 0) {
                                negierteZiffer += 10;
                                borger = 1;
                            } else {
                                borger = 0;
                            }
                        }
                    }

                    // Prüfen ob der Subtrahend noch Nicht-Null-Stellen hat
                    while (posB > auffuellungB) {
                        --posB;
                        if (halbbyteAuslesen(subtrahend, posB) != 0) {
                            ueberlaufErkannt = true;
                            break;
                        }
                    }
                    break;
                }

                --posA;
                --posB;
                int ziffer = halbbyteAuslesen(minuend, posA) - borger;
                ziffer -= halbbyteAuslesen(subtrahend, posB);
                if (ziffer < 0) {
                    ziffer += 10;
                    borger = 1;
                } else {
                    borger = 0;
                }
                minuend = halbbyteSchreiben(minuend, posA, (long) ziffer);
            }
        }

        if (uebertrag != 0) {
            ueberlaufErkannt = true;
        }

        if (bcdIstNull(minuend, minuendLaenge) != 0) {
            vorzeichenPositivSetzen(minuend, minuendLaenge + auffuellungA);
        }

        return ueberlaufErkannt ? BCD_UEBERLAUF : BCD_ERFOLG;
    }

    /**
     * Wandelt eine Ganzzahl in eine gepackte BCD-Darstellung.
     *
     * @param ziel   Ziel-Array für die BCD-Zahl
     * @param laenge Anzahl der Dezimalstellen
     * @param wert   die zu konvertierende Ganzzahl
     * @return {@link #BCD_ERFOLG} oder {@link #BCD_UEBERLAUF}
     */
    private static int ganzzahlNachBcd(byte[] ziel, int laenge, long wert) {
        int auffuellung = 1 - laenge % 2;
        int ende = laenge + auffuellung;
        long restWert = wert;
        ziel = vorzeichenPositivSetzen(ziel, ende);

        while (ende > auffuellung) {
            --ende;
            ziel = halbbyteSchreiben(ziel, ende, restWert % 10L);
            restWert /= 10L;
            if (restWert == 0L) {
                break;
            }
        }

        while (ende > 0) {
            --ende;
            ziel = halbbyteSchreiben(ziel, ende, 0L);
        }

        if (restWert != 0L) {
            return BCD_UEBERLAUF;
        }
        return BCD_ERFOLG;
    }

    /**
     * Wandelt eine gepackte BCD-Darstellung in eine Ganzzahl.
     *
     * @param quelle das BCD-Array
     * @param laenge Anzahl der Dezimalstellen
     * @return die konvertierte Ganzzahl, oder {@link #BCD_UEBERLAUF} bei Überlauf
     */
    private static long bcdNachGanzzahl(byte[] quelle, int laenge) {
        int auffuellung = 1 - laenge % 2;
        int ende = laenge + auffuellung;

        long ganzzahl = 0L;
        for (int i = auffuellung; i < ende; ++i) {
            ganzzahl *= 10L;
            ganzzahl += (long) halbbyteAuslesen(quelle, i);
            if (ganzzahl < 0L) {
                return BCD_UEBERLAUF;
            }
        }

        if (istVorzeichenNegativ(halbbyteAuslesen(quelle, ende))) {
            ganzzahl = -ganzzahl;
        }

        return ganzzahl;
    }

    // =================================================================
    //  Halbbyte-Zugriff (Nibble-Operationen)
    // =================================================================

    /**
     * Liest ein einzelnes BCD-Halbbyte (4 Bit) an der gegebenen Nibble-Position.
     *
     * @param daten    das Byte-Array
     * @param position die Nibble-Position (0-basiert)
     * @return der Wert des Halbbytes (0..15)
     */
    private static int halbbyteAuslesen(byte[] daten, int position) {
        return ((position % 2 != 0) ? daten[position >> 1] : (daten[position >> 1] >> 4))
                & HALBBYTE_UNTERE_MASKE;
    }

    /**
     * Schreibt einen Wert in ein einzelnes BCD-Halbbyte an der gegebenen Nibble-Position.
     *
     * @param daten    das Byte-Array
     * @param position die Nibble-Position (0-basiert)
     * @param wert     der zu schreibende Wert (nur untere 4 Bit relevant)
     * @return Referenz auf das modifizierte Array
     */
    private static byte[] halbbyteSchreiben(byte[] daten, int position, long wert) {
        if (position % 2 != 0) {
            daten[position >> 1] = (byte) ((int) ((long) (daten[position >> 1] & HALBBYTE_OBERE_MASKE)
                    | wert & 15L));
        } else {
            daten[position >> 1] = (byte) ((int) (wert << 4 & 240L
                    | (long) (daten[position >> 1] & HALBBYTE_UNTERE_MASKE)));
        }
        return daten;
    }

    // =================================================================
    //  BCD-Vorzeichen-Hilfsmethoden
    // =================================================================

    /**
     * Prüft ob das Vorzeichen-Halbbyte ein positives Vorzeichen kodiert.
     */
    private static boolean istVorzeichenPositiv(int halbbyte) {
        return !istVorzeichenNegativ(halbbyte);
    }

    /**
     * Prüft ob das Vorzeichen-Halbbyte ein negatives Vorzeichen kodiert.
     * Negativ ist kodiert als 0xD (13) oder 0xB (11).
     */
    private static boolean istVorzeichenNegativ(int halbbyte) {
        return (halbbyte & HALBBYTE_UNTERE_MASKE) == BCD_VORZEICHEN_MINUS
                || (halbbyte & HALBBYTE_UNTERE_MASKE) == 11;
    }

    /**
     * Setzt das Vorzeichen-Halbbyte auf positiv (0xC = 12).
     */
    private static byte[] vorzeichenPositivSetzen(byte[] daten, int position) {
        daten[position >> 1] = (byte) (BCD_VORZEICHEN_PLUS | daten[position >> 1] & HALBBYTE_OBERE_MASKE);
        return daten;
    }

    /**
     * Setzt das Vorzeichen-Halbbyte auf negativ (0xD = 13).
     */
    private static byte[] vorzeichenNegativSetzen(byte[] daten, int position) {
        daten[position >> 1] = (byte) (BCD_VORZEICHEN_MINUS | daten[position >> 1] & HALBBYTE_OBERE_MASKE);
        return daten;
    }

    /**
     * Prüft ob eine gepackte BCD-Zahl den Wert Null hat.
     *
     * @param daten  das BCD-Array
     * @param laenge Anzahl der Dezimalstellen
     * @return 1 wenn alle Ziffern null sind, sonst 0
     */
    private static int bcdIstNull(byte[] daten, int laenge) {
        int index = 0;
        if (laenge <= 0) {
            return 0;
        }
        for (int anzahl = laenge / 2; anzahl > 0; --anzahl) {
            if (daten[index++] != 0) {
                return 0;
            }
        }
        return (daten[0] & HALBBYTE_OBERE_MASKE) != 0 ? 0 : 1;
    }

    // =================================================================
    //  Hex-Konvertierung
    // =================================================================

    /**
     * Wandelt 8 Bytes in 16 Hex-Zeichen um.
     *
     * @param daten die 8 Eingabe-Bytes
     * @return char-Array mit 16 Hex-Zeichen (+ 1 ungenutztes Sentinel-Zeichen)
     */
    private static char[] bytesZuHexZeichen(byte[] daten) {
        int anzahlBytes = 8;
        char[] hexZeichen = new char[17];
        int schreibPos = 0;

        for (int i = 0; i < anzahlBytes; ++i) {
            int byteWert = daten[i] & 255;
            byte[] hexText = Integer.toHexString(byteWert).getBytes();
            if (hexText.length == 1) {
                hexZeichen[schreibPos++] = '0';
                hexZeichen[schreibPos++] = (char) hexText[0];
            } else {
                hexZeichen[schreibPos++] = (char) hexText[0];
                hexZeichen[schreibPos++] = (char) hexText[1];
            }
        }

        return hexZeichen;
    }
}
