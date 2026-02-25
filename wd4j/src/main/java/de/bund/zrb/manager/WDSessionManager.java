package de.bund.zrb.manager;

import de.bund.zrb.api.markerInterfaces.WDModule;
import de.bund.zrb.command.request.WDSessionRequest;
import de.bund.zrb.command.request.parameters.session.parameters.UnsubscribeParameters;
import de.bund.zrb.command.response.WDEmptyResult;
import de.bund.zrb.command.response.WDSessionResult;
import de.bund.zrb.type.browsingContext.WDBrowsingContext;
import de.bund.zrb.type.session.WDSubscription;
import de.bund.zrb.type.session.WDSubscriptionRequest;
import de.bund.zrb.api.WDWebSocketManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class WDSessionManager implements WDModule {
    private final WDWebSocketManager WDWebSocketManager;
    private final Map<WDSubscriptionRequest, String> subscriptionIds = new ConcurrentHashMap<>();
    private final Set<String> subscribedEvents = new HashSet<>();

    /**
     * Erstellt eine neue Session und gibt diese zurück.
     * Da einige Browser einen Standard-Kontext erstellen, wird mit diesem direkt ein neuer Browsing-Kontext erstellt.
     * Damit das Verhalten konsistent ist, wird ein neuer Kontext erstellt, wenn kein Standard-Kontext gefunden wird.
     *
     * @param WDWebSocketManager The high-level api
     * @return Die erstellte Session
     */
    public WDSessionManager(WDWebSocketManager WDWebSocketManager) throws ExecutionException, InterruptedException {
        this.WDWebSocketManager = WDWebSocketManager;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Event Handlers
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Commands
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Ruft den Status der WebDriver BiDi Session ab.
     */
    public WDSessionResult.StatusResult status() {
        return WDWebSocketManager.sendAndWaitForResponse(new WDSessionRequest.Status(), WDSessionResult.StatusResult.class);
    }

    // new() - Since plain "new" is a reserved word in Java!
    /**
     * Erstellt eine neue Session mit dem gegebenen Browser.
     */
    public WDSessionResult.NewResult newSession(String browserName) {
        return WDWebSocketManager.sendAndWaitForResponse(new WDSessionRequest.New(browserName), WDSessionResult.NewResult.class);
    }


    // end() - In corespondance to new!
    /**
     * Beendet die aktuelle WebDriver BiDi Session.
     */
    public void endSession() {
        WDWebSocketManager.sendAndWaitForResponse(new WDSessionRequest.End(), WDEmptyResult.class);
    }

    /**
     * Abonniert WebDriver BiDi Events.
     * Falls bereits abonniert, wird das Event nicht erneut angefordert.
     */
    public WDSessionResult.SubscribeResult subscribe(WDSubscriptionRequest subscriptionRequest) {
        if (subscriptionRequest == null || subscriptionRequest.getEvents().isEmpty()) {
            throw new IllegalArgumentException("Subscription request must not be null or empty.");
        }

        // Prüfe, ob genau diese Subscription bereits existiert
        if (subscriptionIds.containsKey(subscriptionRequest)) {
            System.out.println("Subscription already exists for: " + subscriptionRequest);
            return null;
        }

        // Erzeuge das Command-Objekt mit der ID
        WDSessionRequest.Subscribe subscribeCommand = new WDSessionRequest.Subscribe(subscriptionRequest);

        // Sende den Subscribe-Command
        WDSessionResult.SubscribeResult result = WDWebSocketManager.sendAndWaitForResponse(
                subscribeCommand, WDSessionResult.SubscribeResult.class);

        // Determine subscription ID: prefer the returned subscription from the response (spec-conformant)
        String subscriptionId;
        if (result != null && result.getSubscription() != null
                && result.getSubscription().value() != null && !result.getSubscription().value().trim().isEmpty()) {
            subscriptionId = result.getSubscription().value();
        } else {
            // Fallback: use command ID (Firefox may not return subscription field in older versions)
            subscriptionId = subscribeCommand.getId().toString();
            System.out.println("Warning: No Subscription-ID returned by remote end. Using command ID as fallback: " + subscriptionId);
            result = new WDSessionResult.SubscribeResult(new WDSubscription(subscriptionId));
        }

        // Speichere die Subscription mit ihren vollen Kriterien
        subscriptionIds.put(subscriptionRequest, subscriptionId);

        System.out.println("Subscribed to events: " + subscriptionRequest.getEvents() + " with Subscription-ID: " + subscriptionId);

        return result;
    }

    /**
     * Entfernt die Event-Subscription für WebDriver BiDi Events.
     */
    public void unsubscribe(List<String> events, List<WDBrowsingContext> contexts) {
        if (events == null || events.isEmpty()) {
            throw new IllegalArgumentException("Events list must not be null or empty.");
        }

        List<String> eventsToRemove = new ArrayList<>();
        for (String event : events) {
            // ToDo: Fix this. Es gibt jetzt zwei Listen mit Events: subscribedEvents und subscriptionIds
//            if (subscribedEvents.contains(event)) {
                eventsToRemove.add(event);
//            }
        }

        // Baue die Unsubscribe-Parameter
        UnsubscribeParameters unsubscribeParameters =
                new UnsubscribeParameters.WDUnsubscribeByAttributesRequestParams(eventsToRemove, contexts);

        if (!eventsToRemove.isEmpty()) {
            // 🔹 Unsubscribe-Request mit Events und Contexts senden
            WDSessionRequest.Unsubscribe unsubscribeRequest = new WDSessionRequest.Unsubscribe(unsubscribeParameters);

            WDWebSocketManager.sendAndWaitForResponse(unsubscribeRequest, WDEmptyResult.class);
            subscribedEvents.removeAll(eventsToRemove);

            System.out.println("[INFO] Unsubscribed from events: " + eventsToRemove + " for contexts: " + contexts);
        }
    }


    // Unsubscribe by subscription ID with fallback to by-attributes.
    // The WebDriver BiDi spec supports both variants (union type).
    // Firefox may not support by-ID yet, so we fall back to by-attributes.
    public void unsubscribe(WDSubscription subscription) {
        if (subscription == null || subscription.value().isEmpty()) {
            throw new IllegalArgumentException("Subscription ID must not be null or empty.");
        }

        // Find the original subscription request for potential fallback
        WDSubscriptionRequest originalRequest = null;
        for (Map.Entry<WDSubscriptionRequest, String> entry : subscriptionIds.entrySet()) {
            if (entry.getValue().equals(subscription.value())) {
                originalRequest = entry.getKey();
                break;
            }
        }

        try {
            // Try by-ID first (spec-conformant)
            UnsubscribeParameters unsubscribeParameters =
                    new UnsubscribeParameters.WDUnsubscribeByIDRequestParams(Collections.singletonList(subscription));
            WDWebSocketManager.sendAndWaitForResponse(
                    new WDSessionRequest.Unsubscribe(unsubscribeParameters), WDEmptyResult.class);

            System.out.println("Unsubscribed from event using Subscription-ID: " + subscription.value());
        } catch (Exception e) {
            // Fallback: by-attributes (Firefox may not support by-ID yet)
            System.out.println("Unsubscribe by ID failed (" + e.getMessage()
                    + "), falling back to unsubscribe by attributes.");

            if (originalRequest != null) {
                try {
                    unsubscribe(originalRequest.getEvents(), null);
                } catch (Exception e2) {
                    System.out.println("Unsubscribe by attributes also failed: " + e2.getMessage());
                }
            }
        }

        // Entferne die Subscription aus der Map
        subscriptionIds.entrySet().removeIf(entry -> entry.getValue().equals(subscription.value()));
    }

    /**
     * Entfernt alle aktiven Event-Subscriptions.
     */
    @Deprecated
    public void unsubscribeAll() {
        if (subscriptionIds.isEmpty()) {
            System.out.println("No active subscriptions to remove.");
            return;
        }

        // Erstelle eine Liste mit allen Subscription-IDs
        List<WDSubscription> subscriptionsToRemove = new ArrayList<>();
        for (String subscriptionId : subscriptionIds.values()) {
            subscriptionsToRemove.add(new WDSubscription(subscriptionId));
        }

        // Baue die Unsubscribe-Parameter
        UnsubscribeParameters unsubscribeParameters =
                new UnsubscribeParameters.WDUnsubscribeByIDRequestParams(subscriptionsToRemove);

        // Sende den Unsubscribe-Command für alle aktiven Subscriptions
        WDWebSocketManager.sendAndWaitForResponse(new WDSessionRequest.Unsubscribe(unsubscribeParameters), WDEmptyResult.class);

        // Leere die Subscription-Map
        subscriptionIds.clear();

        System.out.println("Unsubscribed from all events.");
    }
}