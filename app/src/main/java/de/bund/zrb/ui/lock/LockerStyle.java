package de.bund.zrb.ui.lock;

import de.bund.zrb.login.LoginManager;

import java.awt.*;

public enum LockerStyle {

    CLASSIC("- Klassisch – dezentes Swing-Design") {
        @Override
        public LockerUi createUi(Frame parent, LoginManager manager) {
            return new SwingLocker(parent, manager);
        }
    },

    MODERN("🚀 Spacig – schwarz-orange, klare UI") {
        @Override
        public LockerUi createUi(Frame parent, LoginManager manager) {
            return new DefaultLocker(parent, manager);
        }
    },

    RETRO("🕶 Cyber – ASCII Art mit Hacker-Feeling") {
        @Override
        public LockerUi createUi(Frame parent, LoginManager manager) {
            return new RetroLocker(parent, manager);
        }
    },

    CLASSIC_METAL("⚙ Classic Metal – Standard Java-Design") {
        @Override
        public LockerUi createUi(Frame parent, LoginManager manager) {
            return new SwingLocker(parent, manager);
        }
    };

    private final String description;

    LockerStyle(String description) {
        this.description = description;
    }

    public abstract LockerUi createUi(Frame parent, LoginManager manager);

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }
}
