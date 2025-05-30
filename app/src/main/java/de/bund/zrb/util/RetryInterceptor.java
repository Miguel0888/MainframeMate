package de.bund.zrb.util;

import okhttp3.Interceptor;
import okhttp3.Response;

import java.io.IOException;

public class RetryInterceptor implements Interceptor {

    private final int maxRetries;
    private final long initialDelayMillis;

    public RetryInterceptor(int maxRetries, long initialDelayMillis) {
        this.maxRetries = maxRetries;
        this.initialDelayMillis = initialDelayMillis;
    }

    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
        int tryCount = 0;
        IOException lastException = null;
        long delay = initialDelayMillis;

        while (tryCount < maxRetries) {
            try {
                return chain.proceed(chain.request());
            } catch (IOException e) {
                lastException = e;
                tryCount++;
                if (tryCount >= maxRetries) break;
                try {
                    Thread.sleep(delay);
                    delay = Math.min(delay * 2, 180_000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Retry abgebrochen (Thread unterbrochen)", ie);
                }
            }
        }

        throw new IOException("Fehlgeschlagene Anfrage nach " + maxRetries + " Versuchen", lastException);
    }
}
