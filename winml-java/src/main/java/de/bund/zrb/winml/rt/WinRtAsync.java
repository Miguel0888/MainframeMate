package de.bund.zrb.winml.rt;

import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.logging.Logger;

/**
 * Helper for blocking on WinRT {@code IAsyncOperation<T>} results.
 * <p>
 * WinML methods like {@code EvaluateAsync} return an {@code IAsyncOperation}.
 * This helper polls the operation status and retrieves the result once complete.
 * <p>
 * {@code IAsyncInfo} VTable (obtained via QI):
 * <pre>
 *   [0..5] IInspectable
 *   [6] get_Id
 *   [7] get_Status → AsyncStatus enum
 *   [8] get_ErrorCode
 *   [9] Cancel
 *   [10] Close
 * </pre>
 * {@code IAsyncOperation<T>} VTable:
 * <pre>
 *   [0..5] IInspectable
 *   [6] put_Completed
 *   [7] get_Completed
 *   [8] GetResults → out T
 * </pre>
 */
public final class WinRtAsync {

    private static final Logger LOG = Logger.getLogger(WinRtAsync.class.getName());

    // AsyncStatus enum values
    private static final int ASYNC_STATUS_STARTED = 0;
    private static final int ASYNC_STATUS_COMPLETED = 1;
    private static final int ASYNC_STATUS_CANCELED = 2;
    private static final int ASYNC_STATUS_ERROR = 3;

    // IAsyncInfo IID: {00000036-0000-0000-C000-000000000046}
    private static final com.sun.jna.platform.win32.Guid.GUID IID_IASYNCINFO =
            new com.sun.jna.platform.win32.Guid.GUID("{00000036-0000-0000-C000-000000000046}");

    private static final int VT_ASYNCINFO_GET_STATUS = 7;
    private static final int VT_ASYNCINFO_GET_ERRORCODE = 8;

    private static final int VT_ASYNCOP_GET_RESULTS = 8;

    /** Default poll interval in ms. */
    private static final long POLL_INTERVAL_MS = 5;
    /** Default timeout in ms (5 minutes). */
    private static final long DEFAULT_TIMEOUT_MS = 300_000;

    private WinRtAsync() {}

    /**
     * Blocks until the async operation completes and returns the result pointer.
     *
     * @param asyncOp  COM pointer to {@code IAsyncOperation<T>}
     * @param opName   human-readable name for error messages
     * @return the result COM pointer (caller must release)
     * @throws WinRtException if the operation fails or times out
     */
    public static Pointer awaitResult(Pointer asyncOp, String opName) {
        return awaitResult(asyncOp, opName, DEFAULT_TIMEOUT_MS);
    }

    /**
     * Blocks until the async operation completes and returns the result pointer.
     *
     * @param asyncOp    COM pointer to {@code IAsyncOperation<T>}
     * @param opName     human-readable name for error messages
     * @param timeoutMs  maximum wait time in milliseconds
     * @return the result COM pointer (caller must release)
     * @throws WinRtException if the operation fails or times out
     */
    public static Pointer awaitResult(Pointer asyncOp, String opName, long timeoutMs) {
        // QI for IAsyncInfo to poll status
        PointerByReference asyncInfoRef = new PointerByReference();
        int qiHr = ComVTable.call(asyncOp, 0, IID_IASYNCINFO, asyncInfoRef);
        if (qiHr < 0) {
            throw new WinRtException("QI(IAsyncInfo) for " + opName, qiHr);
        }

        Pointer asyncInfo = asyncInfoRef.getValue();
        try {
            long startTime = System.currentTimeMillis();

            while (true) {
                IntByReference statusRef = new IntByReference();
                int hr = ComVTable.call(asyncInfo, VT_ASYNCINFO_GET_STATUS, statusRef);
                if (hr < 0) {
                    throw new WinRtException(opName + ".get_Status", hr);
                }

                int status = statusRef.getValue();

                if (status == ASYNC_STATUS_COMPLETED) {
                    break;
                }

                if (status == ASYNC_STATUS_CANCELED) {
                    throw new WinRtException(opName, 0x800704C7,  // ERROR_CANCELLED
                            "Operation was canceled");
                }

                if (status == ASYNC_STATUS_ERROR) {
                    // Get error code
                    IntByReference errorRef = new IntByReference();
                    ComVTable.call(asyncInfo, VT_ASYNCINFO_GET_ERRORCODE, errorRef);
                    throw new WinRtException(opName, errorRef.getValue(),
                            "Async operation failed");
                }

                // Still in progress
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > timeoutMs) {
                    // Cancel the operation
                    ComVTable.call(asyncInfo, 9); // Cancel
                    throw new WinRtException(opName, 0x80070102,  // WAIT_TIMEOUT
                            "Timed out after " + elapsed + " ms");
                }

                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    ComVTable.call(asyncInfo, 9); // Cancel
                    throw new WinRtException(opName, 0x800704C7, "Interrupted");
                }
            }
        } finally {
            ComVTable.release(asyncInfo);
        }

        // Get the result from IAsyncOperation<T>::GetResults
        PointerByReference resultRef = new PointerByReference();
        int getResultHr = ComVTable.call(asyncOp, VT_ASYNCOP_GET_RESULTS, resultRef);
        if (getResultHr < 0) {
            throw new WinRtException(opName + ".GetResults", getResultHr);
        }

        return resultRef.getValue();
    }
}

