package de.bund.zrb.wiki.infrastructure;

import de.bund.zrb.wiki.domain.WikiCredentials;
import de.bund.zrb.wiki.domain.WikiSiteDescriptor;
import net.sourceforge.jwbf.mediawiki.bots.MediaWikiBot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages JWBF bot instances per site+user.
 * Reuses bots to keep login sessions alive.
 */
final class JwbfBotProvider {

    private static final Logger LOG = Logger.getLogger(JwbfBotProvider.class.getName());
    private final Map<String, MediaWikiBot> bots = new ConcurrentHashMap<String, MediaWikiBot>();

    MediaWikiBot getBot(WikiSiteDescriptor site, WikiCredentials credentials) {
        String key = botKey(site, credentials);
        MediaWikiBot existing = bots.get(key);
        if (existing != null) {
            return existing;
        }

        LOG.info("[Wiki] Creating bot for " + site.displayName() + " at " + site.apiUrl());
        MediaWikiBot bot = new MediaWikiBot(site.apiUrl());

        if (site.requiresLogin() && credentials != null && !credentials.isAnonymous()) {
            try {
                bot.login(credentials.username(), new String(credentials.password()));
                LOG.info("[Wiki] Logged in as " + credentials.username());
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[Wiki] Login failed for " + credentials.username(), e);
                throw new RuntimeException("Wiki login failed: " + e.getMessage(), e);
            }
        }

        bots.put(key, bot);
        return bot;
    }

    void invalidate(WikiSiteDescriptor site, WikiCredentials credentials) {
        String key = botKey(site, credentials);
        bots.remove(key);
    }

    private String botKey(WikiSiteDescriptor site, WikiCredentials credentials) {
        String user = (credentials != null && !credentials.isAnonymous())
                ? credentials.username() : "anonymous";
        return site.id().value() + "|" + user;
    }
}

