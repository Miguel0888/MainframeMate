package de.bund.zrb.winml;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.ptr.PointerByReference;
import de.bund.zrb.winml.rt.ComBase;
import de.bund.zrb.winml.rt.HString;
import de.bund.zrb.winml.rt.WinRtRuntime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Finds the correct IIDs by QI-probing the live WinRT activation factory.
 */
@EnabledOnOs(OS.WINDOWS)
class IidProbeTest {

    private static final Guid.GUID IID_IACTIVATION_FACTORY =
            new Guid.GUID("{00000035-0000-0000-C000-000000000046}");

    // Known candidate IIDs for ILearningModelDeviceFactory from various SDK versions
    private static final String[] CANDIDATE_IIDS = {
            "{9CFD8A0A-7420-47F5-AC2B-0EF5B04E9F52}", // ILearningModelDeviceStatics?
            "{9D03EDDC-85C7-4E56-AA14-FE4A1A44AAAF}", // ILearningModelDeviceFactory candidate
            "{C95F7A7A-E788-475E-8917-23AA381FAF0B}", // ILearningModelBindingFactory
            "{E3B977E8-6952-4E47-8EF4-1F7F07897C6D}", // ILearningModelStatics
            "{0F6B881D-1C9B-47B2-BF08-25628D7D16AD}", // ILearningModelSessionFactory
            // More from Win10 SDK:
            "{9D03EDDC-85C7-4E46-AA13-FE4A1A44AAAF}", // variant
            "{D5F0F5D0-5A77-4F5A-8A91-5EC04E7C7EA0}", // possible factory
    };

    @Test
    void probeLearningModelDeviceInterfaces() {
        WinRtRuntime.initialize();

        String className = "Windows.AI.MachineLearning.LearningModelDevice";
        try (HString hs = HString.create(className)) {
            // Get IActivationFactory first
            PointerByReference afRef = new PointerByReference();
            WinNT.HRESULT hr = ComBase.INSTANCE.RoGetActivationFactory(
                    hs.getHandle(), IID_IACTIVATION_FACTORY, afRef);
            assert hr.intValue() == 0 : "Failed to get IActivationFactory";

            Pointer factory = afRef.getValue();
            System.out.println("Got IActivationFactory for " + className);

            // Get IInspectable::GetIids (VTable[3])
            // GetIids returns the count + array of IIDs the object supports
            PointerByReference iidCountRef = new PointerByReference();
            PointerByReference iidsArrayRef = new PointerByReference();

            Pointer vtable = factory.getPointer(0);
            // [3] = GetIids(out ULONG iidCount, out IID** iids)
            Function getIids = Function.getFunction(
                    vtable.getPointer(3L * Native.POINTER_SIZE), Function.ALT_CONVENTION);
            int getIidsHr = getIids.invokeInt(new Object[]{factory, iidCountRef, iidsArrayRef});
            System.out.println("GetIids → HRESULT 0x" + Integer.toHexString(getIidsHr));

            if (getIidsHr == 0 && iidCountRef.getValue() != null) {
                long count = Pointer.nativeValue(iidCountRef.getValue());
                System.out.println("IID count: " + count);

                if (count > 0 && count < 100 && iidsArrayRef.getValue() != null) {
                    Pointer arr = iidsArrayRef.getValue();
                    for (long i = 0; i < count; i++) {
                        // Each GUID is 16 bytes
                        long offset = i * 16;
                        int data1 = arr.getInt(offset);
                        short data2 = arr.getShort(offset + 4);
                        short data3 = arr.getShort(offset + 6);
                        byte[] data4 = arr.getByteArray(offset + 8, 8);
                        String iid = String.format("{%08X-%04X-%04X-%02X%02X-%02X%02X%02X%02X%02X%02X}",
                                data1, data2 & 0xFFFF, data3 & 0xFFFF,
                                data4[0], data4[1], data4[2], data4[3],
                                data4[4], data4[5], data4[6], data4[7]);
                        System.out.println("  IID[" + i + "]: " + iid);
                    }
                }
            }

            // Also probe the candidates
            System.out.println("\nQI probe results:");
            for (String iid : CANDIDATE_IIDS) {
                try {
                    PointerByReference qiRef = new PointerByReference();
                    Function qi = Function.getFunction(
                            vtable.getPointer(0), Function.ALT_CONVENTION);
                    int qiHr = qi.invokeInt(new Object[]{factory, new Guid.GUID(iid), qiRef});
                    System.out.println("  " + iid + " → 0x" + Integer.toHexString(qiHr)
                            + (qiHr == 0 ? " ✓ FOUND!" : ""));
                    if (qiHr == 0 && qiRef.getValue() != null) {
                        // Release the QI'd interface
                        Pointer vt2 = qiRef.getValue().getPointer(0);
                        Function rel = Function.getFunction(
                                vt2.getPointer(2L * Native.POINTER_SIZE), Function.ALT_CONVENTION);
                        rel.invokeInt(new Object[]{qiRef.getValue()});
                    }
                } catch (Exception e) {
                    System.out.println("  " + iid + " → ERROR: " + e.getMessage());
                }
            }

            // Release factory
            Function rel = Function.getFunction(
                    vtable.getPointer(2L * Native.POINTER_SIZE), Function.ALT_CONVENTION);
            rel.invokeInt(new Object[]{factory});
        }
    }
}

